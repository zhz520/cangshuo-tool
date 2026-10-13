"""Local synthetic UI fixture server; not a platform parser or a deployment service.

Run from the repo root. Used to exercise album/video layouts and error/cancellation UI
without contacting external platforms. Binds 127.0.0.1 only and never writes user input.
"""
import base64
import datetime
import http.server
import json
import pathlib
import time
import urllib.parse

ROOT = pathlib.Path(__file__).resolve().parents[1] / 'deploy' / 'site'
BASE = '/api/v1/web-tools/short_video_parse'
ID = 'a' * 32
PNG = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=')


class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(ROOT), **kwargs)

    def log_message(self, *args):
        pass

    def envelope(self, data=None, code=0, status=200):
        raw = json.dumps(dict(code=code, data=data, traceId='b' * 32)).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(raw)))
        self.send_header('Cache-Control', 'no-store')
        self.end_headers()
        try:
            self.wfile.write(raw)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def do_GET(self):
        if self.path == BASE + '/capabilities':
            self.envelope(dict(available=True, maxFileBytes=209715200, maxZipBytes=33554432))
        elif self.path.startswith(BASE + '/assets/'):
            self.send_response(200)
            self.send_header('Content-Type', 'image/png')
            self.send_header('Content-Length', str(len(PNG)))
            self.send_header('Content-Disposition', 'attachment; filename="douyin_image_1.png"' if 'download=true' in self.path else 'inline')
            self.end_headers()
            self.wfile.write(PNG)
        elif self.path.startswith(BASE + '/albums/'):
            self.envelope(code=61005, status=413)
        else:
            super().do_GET()

    def do_DELETE(self):
        self.envelope()

    def do_POST(self):
        if self.path == '/api/v1/tools/short_video_parse/usage':
            return self.envelope()
        raw = self.rfile.read(min(16384, int(self.headers.get('Content-Length', '0'))))
        text = json.loads(raw).get('text', '')
        if 'slow' in text:
            time.sleep(5)
        if 'error' in text:
            return self.envelope(code=61002, status=422)
        kind = 'VIDEO' if '/video/' in text else 'IMAGE'
        types = ['VIDEO', 'VIDEO', 'COVER'] if kind == 'VIDEO' else ['IMAGE'] * 6 + ['COVER']
        assets = []
        for index, item in enumerate(types):
            path = BASE + '/assets/' + ID + '/' + str(index)
            assets.append(dict(index=index, kind=item, label='', width=1080, height=1920,
                               bitrate=2000000 - index * 100000, source='PLATFORM_SOURCE',
                               previewPath=path, downloadPath=path + '?download=true',
                               directUrl='https://p.douyinpic.com/' + str(index) + '.jpg'))
        self.envelope(dict(id=ID, platform='douyin', platformName='抖音',
            sourceUrl='https://www.douyin.com/' + ('video' if kind == 'VIDEO' else 'note') + '/1234567890',
            title='界面验证 · 合成测试作品 / Synthetic UI fixture', description='此页仅用于验证界面交互。图片为测试资源，不是第三方解析结果。',
            author='UI test', durationSeconds=12 if kind == 'VIDEO' else 0,
            expiresAt=(datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(minutes=15)).isoformat(),
            assets=assets, albumPath=BASE + '/albums/' + ID))


if __name__ == '__main__':
    server = http.server.ThreadingHTTPServer(('127.0.0.1', 8088), Handler)
    print('Synthetic UI test server: http://127.0.0.1:8088/tools/short_video_parse/', flush=True)
    server.serve_forever()
