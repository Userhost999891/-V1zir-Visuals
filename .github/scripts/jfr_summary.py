"""Summarises the game test's Java Flight Recorder files: hottest methods on the render thread and
the biggest allocation sites, so CI shows where frame time goes."""
import collections
import re
import subprocess
import sys

FRAME = re.compile(r'^\s+([\w$.<>/]+)\(.*?\)\s+line:')


def events(path, kind):
	out = subprocess.run(['jfr', 'print', '--stack-depth', '96', '--events', kind, path],
						 capture_output=True, text=True, check=True).stdout
	event = None
	for line in out.splitlines():
		if line.startswith(kind + ' {'):
			event = {'frames': [], 'thread': '', 'weight': 0, 'class': ''}
		elif line == '}' and event is not None:
			yield event
			event = None
		elif event is not None:
			s = line.strip()
			m = FRAME.match(line)
			if m:
				event['frames'].append(m.group(1))
			elif s.startswith('sampledThread =') or s.startswith('eventThread ='):
				event['thread'] = s.split('"')[1] if '"' in s else s
			elif s.startswith('weight ='):
				num = re.findall(r'[\d.]+', s)
				unit = s.split()[-1]
				w = float(num[0]) if num else 0
				event['weight'] = w * {'kB': 1e3, 'MB': 1e6, 'GB': 1e9}.get(unit, 1)
			elif s.startswith('objectClass ='):
				event['class'] = s.split('=', 1)[1].split('(')[0].strip()


def top(counter, total, n, title):
	print(f'--- {title}')
	for name, c in counter.most_common(n):
		print(f'{100.0 * c / max(total, 1):6.2f}%  {name}')


for path in sys.argv[1:]:
	print(f'=== JFR {path}')
	samples = [e for e in events(path, 'jdk.ExecutionSample') if e['thread'] == 'Render thread']
	total = len(samples)
	print(f'render thread samples: {total}')
	self_time = collections.Counter(e['frames'][0] for e in samples if e['frames'])
	inclusive = collections.Counter()
	mod = collections.Counter()
	for e in samples:
		seen = set(e['frames'])
		inclusive.update(seen)
		mod.update(f for f in seen if 'v1zir' in f)
	top(self_time, total, 30, 'self time (render thread)')
	top(inclusive, total, 60, 'inclusive time (render thread)')
	top(mod, total, 40, 'inclusive time, V1zir code only')
	allocations = collections.Counter()
	sites = collections.Counter()
	for e in events(path, 'jdk.ObjectAllocationSample'):
		allocations[e['class']] += e['weight']
		site = next((f for f in e['frames'] if 'v1zir' in f), None)
		if site:
			sites[f"{site} -> {e['class']}"] += e['weight']
	print('--- allocations by class (MB)')
	for name, w in allocations.most_common(20):
		print(f'{w / 1e6:9.1f}  {name}')
	print('--- allocations from V1zir code (MB)')
	for name, w in sites.most_common(25):
		print(f'{w / 1e6:9.1f}  {name}')
	gcs = list(events(path, 'jdk.GarbageCollection'))
	print(f'--- garbage collections: {len(gcs)}')
