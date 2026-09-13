"""Retry one official Maven artifact with verified ranged HTTPS downloads."""
from concurrent.futures import ThreadPoolExecutor
import hashlib
from pathlib import Path
import time
import urllib.request

name = 'text-recognition-bundled-common'
version = '17.0.0'
relative = f'com/google/mlkit/{name}/{version}'
base = f'https://dl.google.com/dl/android/maven2/{relative}/{name}-{version}'
dest = Path(__file__).resolve().parents[1] / '.tools' / 'maven' / relative
dest.mkdir(parents=True, exist_ok=True)

def fetch(url):
    for attempt in range(4):
        try:
            with urllib.request.urlopen(url, timeout=45) as r:
                return r.read()
        except Exception:
            if attempt == 3:
                raise
            time.sleep(2)

pom = fetch(base + '.pom')
assert hashlib.sha1(pom).hexdigest() == fetch(base + '.pom.sha1').decode().strip()
(dest / f'{name}-{version}.pom').write_bytes(pom)
expected = fetch(base + '.aar.sha1').decode().strip()
probe = urllib.request.Request(base + '.aar', headers={'Range': 'bytes=0-0'})
with urllib.request.urlopen(probe, timeout=45) as r:
    assert r.status == 206, 'Server must support ranges'
    total = int(r.headers['Content-Range'].split('/')[-1])
    r.read()
chunk_size = 1024 * 1024

def part(start):
    end = min(start + chunk_size, total) - 1
    for attempt in range(5):
        try:
            req = urllib.request.Request(base + '.aar', headers={'Range': f'bytes={start}-{end}'})
            with urllib.request.urlopen(req, timeout=60) as r:
                assert r.status == 206
                assert r.headers['Content-Range'].startswith(f'bytes {start}-{end}/')
                data = r.read()
                assert len(data) == end - start + 1
                return data
        except Exception:
            if attempt == 4:
                raise
            time.sleep(2)

target = dest / f'{name}-{version}.aar'
with ThreadPoolExecutor(max_workers=4) as pool, target.open('wb') as out:
    for index, data in enumerate(pool.map(part, range(0, total, chunk_size))):
        out.write(data)
        print(f'OCR artifact: {out.tell()}/{total} bytes', flush=True)
assert hashlib.sha1(target.read_bytes()).hexdigest() == expected, 'Maven artifact checksum mismatch'
print('Official OCR artifact and POM verified.', flush=True)
