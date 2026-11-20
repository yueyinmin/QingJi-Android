import base64, json, os, ssl, subprocess, urllib.request
ROOT = os.getcwd()
OWNER, REPO = 'yueyinmin', 'QingJi-Android'
BRANCH = 'feature/experience-polish-v2'
BASE = 'ae91f73'
ctx = ssl._create_unverified_context()
p = subprocess.run(['git','credential','fill'], input=b'protocol=https\nhost=github.com\n\n',
                   stdout=subprocess.PIPE, stderr=subprocess.PIPE)
token = None
for line in p.stdout.decode('utf-8','ignore').splitlines():
    if line.startswith('password='): token = line.split('=',1)[1]
if not token:
    raise SystemExit('NO_TOKEN')
files = subprocess.run(['git','diff','--name-only', BASE+'..2510d82'],
                       stdout=subprocess.PIPE, text=True).stdout.splitlines()
files = [f for f in files if f.strip()]
def api(path, method='GET', body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request('https://api.github.com/repos/%s/%s/%s' % (OWNER, REPO, path),
                                 data=data, method=method,
                                 headers={'Authorization':'Bearer '+token,
                                          'Accept':'application/vnd.github+json',
                                          'Content-Type':'application/json'})
    with urllib.request.urlopen(req, timeout=120, context=ctx) as r:
        return json.loads(r.read().decode('utf-8'))
base_commit = api('git/commits/' + BASE)
tree_sha = base_commit['tree']['sha']
entries = []
for i, path in enumerate(files, 1):
    full = os.path.join(ROOT, path)
    content = open(full,'rb').read()
    blob = api('git/blobs', 'POST', {'content': base64.b64encode(content).decode(), 'encoding':'base64'})
    entries.append({'path': path, 'mode':'100644', 'type':'blob', 'sha': blob['sha']})
    print('blob %d/%d %s' % (i, len(files), path), flush=True)
tree = api('git/trees', 'POST', {'base_tree': tree_sha, 'tree': entries})
commit = api('git/commits', 'POST', {
    'message': 'feat(food): OmniFood8K ETL 与只读标准库接入 + 老乡鸡仓库审计归档',
    'tree': tree['sha'], 'parents': [BASE]})
api('git/refs/heads/' + BRANCH, 'PATCH', {'sha': commit['sha'], 'force': False})
print('PUSHED', commit['sha'][:7], 'files', len(files))
