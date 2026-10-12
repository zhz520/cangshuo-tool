"""Isolated MySQL + API acceptance on the deployment host (Python 2/3 stdlib).

Never touches the production schema. Creates uniquely named temporary DB/user/container,
boots the candidate image with fresh test secrets and removes only those resources in finally.
Usage: python scripts/check_admin_statistics_runtime.py <candidate-image>
"""
from __future__ import print_function
from multiprocessing.dummy import Pool
import json
import subprocess
import sys
import time
import threading
import uuid
try:
    from urllib.request import Request, urlopen
    from urllib.error import HTTPError
except ImportError:
    from urllib2 import Request, urlopen, HTTPError


def command(args, data=None):
    process = subprocess.Popen(args, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    out, error = process.communicate(None if data is None else data.encode('utf-8'))
    if process.returncode:
        raise RuntimeError('runtime command failed; arguments and sensitive output omitted')
    return out.decode('utf-8')


def inspect(name):
    return json.loads(command(['docker', 'inspect', name]))[0]


def sql(statement):
    return command(['docker', 'exec', '-i', 'cangshuo-toolbox-production-mysql-1', 'sh', '-c',
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --batch --skip-column-names'], statement)


def run(image, extra_checks=None, candidate_environment=None):
    suffix = uuid.uuid4().hex[:12]
    database = 'codex_stats_' + suffix
    user = 'stats_' + suffix
    container = 'codex-stats-' + suffix
    password = uuid.uuid4().hex + uuid.uuid4().hex
    admin_password = uuid.uuid4().hex
    created_db = False
    checks = [0]
    check_lock = threading.Lock()
    def check(condition, name):
        if not condition:
            raise AssertionError(name + ' (response content omitted)')
        with check_lock:
            checks[0] += 1
            print('PASS', checks[0], name)
    try:
        source = inspect('cangshuo-toolbox-production-server-1')
        environment = dict(pair.split('=', 1) for pair in source['Config']['Env'] if '=' in pair)
        network = next(name for name in source['NetworkSettings']['Networks'] if name.endswith('_backend'))
        edge = next(name for name in source['NetworkSettings']['Networks'] if name.endswith('_edge'))
        sql("CREATE DATABASE `%s`; CREATE USER '%s'@'%%' IDENTIFIED BY '%s'; GRANT ALL ON `%s`.* TO '%s'@'%%';" %
            (database, user, password, database, user))
        created_db = True
        environment.update(DB_NAME=database, DB_USERNAME=user, DB_PASSWORD=password,
            JWT_SECRET=uuid.uuid4().hex + uuid.uuid4().hex, ADMIN_BOOTSTRAP_ENABLED='true',
            ADMIN_BOOTSTRAP_USERNAME='stats_check', ADMIN_BOOTSTRAP_PASSWORD=admin_password,
            RATE_LIMIT_ENABLED='false', AI_ENABLED='false', STORAGE_ENABLED='false')
        if candidate_environment:
            environment.update(candidate_environment)
        # An internal-only Docker network does not publish a host port. Mirror both app networks.
        args = ['docker', 'create', '--name', container, '--network', edge, '-p', '127.0.0.1::8080']
        for key, value in environment.items():
            args.extend(['-e', key + '=' + value])
        args.append(image)
        command(args)
        command(['docker', 'network', 'connect', network, container])
        command(['docker', 'start', container])
        port = None
        for attempt in range(20):
            state = inspect(container)
            ports = state['NetworkSettings'].get('Ports') or {}
            if ports.get('8080/tcp'):
                port = ports['8080/tcp'][0]['HostPort']
                break
            if state['State']['Status'] == 'exited':
                process = subprocess.Popen(['docker', 'logs', '--tail', '25', container], stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
                logs = process.communicate()[0].decode('utf-8', 'replace')
                for key, value in environment.items():
                    if any(word in key for word in ('PASSWORD', 'SECRET', 'TOKEN', 'KEY')) and value:
                        logs = logs.replace(value, '[redacted]')
                print(logs[-6000:])
                raise RuntimeError('candidate exited before port binding')
            time.sleep(1)
        check(port is not None, 'isolated HTTP port available')
        base = 'http://127.0.0.1:' + port + '/api/v1/'
        def request(path, payload=None, token=None, method=None):
            headers = {'Accept': 'application/json'}
            if token:
                headers['Authorization'] = 'Bearer ' + token
            data = None
            if payload is not None:
                headers['Content-Type'] = 'application/json'
                data = json.dumps(payload).encode('utf-8')
            req = Request(base + path, data=data, headers=headers)
            if method:
                req.get_method = lambda: method
            try:
                response = urlopen(req, timeout=15)
            except HTTPError as error:
                response = error
            body = json.loads(response.read().decode('utf-8'))
            status = response.getcode()
            cache = response.headers.get('Cache-Control')
            trace = response.headers.get('X-Trace-Id')
            response.close()
            check(body['traceId'] == trace, 'trace header matches envelope')
            return status, body, cache
        ready = False
        for attempt in range(45):
            try:
                if request('health')[0] == 200:
                    ready = True
                    break
            except Exception:
                pass
            time.sleep(2)
        check(ready, 'isolated candidate health and Flyway migration')
        def test_sql(statement):
            return sql('USE `%s`; %s' % (database, statement)).strip()
        check(test_sql("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1 AND version = '43'") == '1', 'V43 applied')
        check(request('admin/statistics')[0] == 401, 'anonymous statistics denied')
        status, body, unused = request('admin/auth/login', {'username': 'stats_check', 'password': admin_password})
        check(status == 200, 'isolated administrator login')
        admin = body['data']['accessToken']
        snapshots = []
        for days in (7, 30, 90):
            status, body, cache = request('admin/statistics?days=' + str(days), token=admin)
            data = body['data']
            check(status == 200 and cache == 'no-store' and len(data['daily']) == days, 'window + cache ' + str(days))
            check(len(set(row['date'] for row in data['daily'])) == days and data['timezone'] == 'Asia/Shanghai', 'dates + timezone ' + str(days))
            check(data['summary']['allTimeOpens'] == 0 and data['summary']['users'] == 0, 'fresh database zero values')
            snapshots.append(data)
        check(request('admin/statistics?days=8', token=admin)[0] == 400, 'unsupported window rejected')
        check(request('admin/statistics?days=abc', token=admin)[0] == 400, 'malformed window rejected')
        def open_tool(unused):
            return request('tools/calculator/usage', method='POST')[0]
        pool = Pool(4)
        try:
            check(all(status == 200 for status in pool.map(open_tool, range(12))), 'concurrent opens succeed')
        finally:
            pool.close()
            pool.join()
        data = request('admin/statistics?days=7', token=admin)[1]['data']
        check(data['summary']['allTimeOpens'] == 12 and data['summary']['windowOpens'] == 12, 'daily and cumulative opens agree')
        check(data['daily'][-1]['opens'] == 12 and data['ranking'][0]['opens'] == 12, 'trend and ranking agree')
        request('tools/missing/usage', method='POST')
        check(test_sql('SELECT SUM(use_count) FROM tool_usage_stat') == '12', 'unknown tool does not count')
        email = 'stats-' + suffix + '@example.invalid'
        user_password = uuid.uuid4().hex
        status, body, unused = request('auth/register', {'email': email, 'password': user_password, 'nickname': 'stats-test'})
        check(status == 201, 'ephemeral user registration')
        token = body['data']['accessToken']
        check(request('admin/statistics', token=token)[0] == 403, 'ordinary user statistics denied')
        for unused in range(3):
            check(request('auth/me', token=token)[0] == 200, 'successful authenticated activity')
        data = request('admin/statistics', token=admin)[1]['data']
        check(data['summary']['users'] == 1 and data['summary']['newUsers'] == 1, 'registration totals')
        check(data['summary']['dailyActiveUsers'] == 1 and data['summary']['weeklyActiveUsers'] == 1, 'daily account deduplication')
        check(test_sql('SELECT COUNT(*) FROM user_activity_daily') == '1', 'admins and repeated requests do not add active rows')
        check(data['traffic']['clientErrors'] >= 5 and data['traffic']['requests'] >= 20, 'security rejections tracked')
        check(data['traffic']['activityWriteFailures'] == 0, 'activity collection operational')
        # State summaries: permanent, expired and disabled PRO memberships have distinct semantics.
        test_sql("UPDATE sys_user SET plan='PRO', plan_expires_at=NULL")
        check(request('admin/statistics', token=admin)[1]['data']['summary']['activeMembers'] == 1, 'permanent active membership')
        test_sql("UPDATE sys_user SET plan_expires_at=UTC_TIMESTAMP(3)-INTERVAL 1 SECOND")
        data = request('admin/statistics', token=admin)[1]['data']
        check(data['summary']['activeMembers'] == 0 and data['summary']['expiredMembers'] == 1, 'expired membership excluded')
        test_sql("UPDATE sys_user SET status=0, plan_expires_at=NULL")
        data = request('admin/statistics', token=admin)[1]['data']
        check(data['summary']['activeMembers'] == 0 and data['summary']['disabledUsers'] == 1, 'disabled account excluded from effective membership')
        test_sql('UPDATE sys_user SET status=1')
        status, body, unused = request('auth/me', {'email': email, 'password': user_password}, token=token, method='DELETE')
        check(status == 200, 'self deletion succeeds')
        check(test_sql('SELECT COUNT(*) FROM user_activity_daily') == '0', 'account deletion cascades activity rows')
        check(request('admin/statistics', token=admin)[1]['data']['summary']['users'] == 0, 'deleted account no longer counted')
        if extra_checks is not None:
            extra_checks(check, request, test_sql, admin)
        print('Isolated statistics acceptance:', checks[0], 'checks passed')
    finally:
        subprocess.call(['docker', 'rm', '-f', container], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if created_db:
            sql("DROP DATABASE `%s`; DROP USER '%s'@'%%';" % (database, user))
        print('Isolated container/database/user cleanup finished')


if __name__ == '__main__':
    run(sys.argv[1])
