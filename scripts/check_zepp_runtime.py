"""Isolated V49/V50 catalog and Zepp API acceptance; never contacts real Zepp accounts.

Uses deliberately invalid protocol keys only in the temporary candidate container.
All submit cases fail validation or catalog guards before any upstream call.
"""
from __future__ import print_function
import os
import sys
from check_admin_statistics_runtime import run
from check_calc_catalog_runtime import verify as verify_local_catalog


def verify(check, request, sql, admin):
    verify_local_catalog(check, request, sql, admin)
    check(sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version='50' AND success=1") == '1', 'V50 applied')
    status, body, unused = request('tools/zepp_steps')
    row = body['data']
    print('Zepp public metadata:', dict((key, row.get(key)) for key in ('code','mode','categoryCode','sortOrder','requiresLogin')))
    check(status == 200 and row['mode'] == 'WEB' and row['categoryCode'] == 'LIFE'
          and row['sortOrder'] == 510 and not row['requiresLogin'], 'Zepp WEB metadata')
    path = 'web-tools/zepp_steps/'
    status, body, cache = request(path + 'capabilities')
    check(status == 200 and cache == 'no-store' and body['data']['available'], 'anonymous capability with no-store')
    check(body['data']['minSteps'] == 1 and body['data']['maxSteps'] == 50000
          and body['data']['cooldownSeconds'] == 60 and body['data']['timeoutSeconds'] == 30, 'Zepp bounds contract')
    for value in (0, -1, 50001, 1.5, '10000', None):
        status, body, cache = request(path + 'submit', {'account': 'test@example.invalid',
            'password': 'not-a-real-password', 'steps': value, 'consent': True}, method='POST')
        check(status == 400 and body['code'] == 10001 and cache == 'no-store', 'invalid steps rejected without upstream call')
    for value in (False, None, 'true', 1):
        status, body, unused = request(path + 'submit', {'account': 'test@example.invalid',
            'password': 'not-a-real-password', 'steps': 10000, 'consent': value}, method='POST')
        check(status == 400 and body['code'] == 10001, 'explicit boolean consent required')
    check(request(path + 'submit', {}, method='PUT')[0] == 401, 'anonymous permission limited to exact GET and POST')
    check(request('admin/tools/zepp_steps')[0] == 401, 'anonymous Zepp management denied')
    check(request('admin/tools/zepp_steps', token=admin)[0] == 200, 'Zepp admin detail available')
    check(request('admin/tools/zepp_steps/status', {'status': 'MAINTENANCE'}, token=admin, method='PATCH')[0] == 200,
          'administrator can maintain Zepp')
    check(not request(path + 'capabilities')[1]['data']['available'], 'maintenance disables capability')
    status, body, cache = request(path + 'submit', {'account': 'test@example.invalid',
        'password': 'not-a-real-password', 'steps': 10000, 'consent': True}, method='POST')
    check(status == 503 and body['code'] == 10008 and cache == 'no-store', 'maintenance blocks execution before Zepp')
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    with open(os.path.join(root, 'server/src/main/resources/db/migration/V50__seed_zepp_steps_web_tool.sql'), 'rb') as stream:
        sql(stream.read().decode('utf-8'))
    check(sql("SELECT status FROM tool_definition WHERE tool_code='zepp_steps'") == 'MAINTENANCE', 'V50 replay preserves administrator state')
    check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='zepp_steps'") == '1', 'V50 replay is idempotent')
    check(request('admin/tools/zepp_steps/status', {'status': 'ENABLED'}, token=admin, method='PATCH')[0] == 200,
          'administrator can enable Zepp')
    sql("UPDATE tool_category SET status=0 WHERE code='LIFE'")
    check(not request(path + 'capabilities')[1]['data']['available'], 'category off disables Zepp capability')
    status, body, unused = request(path + 'submit', {'account': 'test@example.invalid',
        'password': 'not-a-real-password', 'steps': 10000, 'consent': True}, method='POST')
    check(status == 503 and body['code'] == 10008, 'category off blocks execution')
    sql("UPDATE tool_category SET status=1 WHERE code='LIFE'")
    check(request('tools/zepp_steps/usage', method='POST')[0] == 200, 'Zepp uses shared tool-open counter')
    ranking = request('admin/statistics?days=7', token=admin)[1]['data']['ranking']
    check(any(row['code'] == 'zepp_steps' and row['opens'] == 1 for row in ranking), 'Zepp participates in admin ranking')


if __name__ == '__main__':
    run(sys.argv[1], extra_checks=verify, candidate_environment={
        'ZEPP_ENABLED': 'true', 'ZEPP_LOGIN_KEY': 'test-key-16bytes', 'ZEPP_LOGIN_IV': 'test--iv-16bytes'})
