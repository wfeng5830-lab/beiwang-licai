"""Download official Gradle with bounded retries and SHA-256 verification."""
import hashlib
import pathlib
import time
import urllib.request
import zipfile
from concurrent.futures import ThreadPoolExecutor

root = pathlib.Path(__file__).resolve().parents[1] / '.tools'
root.mkdir(exist_ok=True)
url = 'https://downloads.gradle.org/distributions/gradle-8.11.1-bin.zip'
target = root / 'gradle.zip'
def get_part(start):
    end = min(start + 2 * 1024 * 1024, 136920070) - 1
    for attempt in range(5):
        try:
            request = urllib.request.Request(url, headers={'Range': f'bytes={start}-{end}'})
            with urllib.request.urlopen(request, timeout=90) as response:
                if response.status != 206 or not response.headers.get('Content-Range', '').startswith(f'bytes {start}-{end}/'):
                    raise ValueError('Unexpected range response')
                chunk = response.read()
                if len(chunk) != end - start + 1:
                    raise ValueError('Incomplete range')
                return chunk
        except Exception:
            if attempt == 4:
                raise
            time.sleep(2)

for attempt in range(2):
    try:
        expected = urllib.request.urlopen(url + '.sha256', timeout=45).read().decode().strip().split()[0]
        with ThreadPoolExecutor(max_workers=4) as executor, target.open('wb') as output:
            for index, chunk in enumerate(executor.map(get_part, range(0, 136920070, 2 * 1024 * 1024))):
                output.write(chunk)
                if index % 8 == 0:
                    print(f'Downloaded {output.tell() // 1048576} MB / 131 MB', flush=True)
        actual = hashlib.sha256(target.read_bytes()).hexdigest()
        if actual != expected:
            raise ValueError('Gradle checksum mismatch; download is incomplete')
        with zipfile.ZipFile(target) as archive:
            archive.extractall(root)
        print('Gradle 8.11.1 downloaded, verified and extracted.', flush=True)
        break
    except Exception as error:
        print(f'Attempt {attempt + 1}: {error}', flush=True)
        if attempt == 1:
            raise
        time.sleep(3)
