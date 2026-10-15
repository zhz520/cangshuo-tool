# -*- coding: utf-8 -*-
"""V57 Chinese conversion catalog/admin/statistics acceptance (Python 2/3 stdlib).

Reuse the V56 metadata/statistics assertions and check_admin_statistics_runtime.run.
The harness reads the deployment host's Docker configuration and creates a temporary
schema/user/candidate container; only that schema receives edits, seed replays and
synthetic opens. Its finally block removes those resources. No conversion API exists.

Source review, 2026-10-15: OpenCC ver.1.1.9, commit
556ed22496d650bd0b13b6c163be9814637970ae. Official configuration references:
https://github.com/BYVoid/OpenCC/blob/ver.1.1.9/data/config/s2t.json
https://github.com/BYVoid/OpenCC/blob/ver.1.1.9/data/config/s2twp.json
https://github.com/BYVoid/OpenCC/blob/ver.1.1.9/data/config/s2hk.json
The phrase/character chains and Taiwan/Hong Kong variants justify the metadata;
the Android implementation owns conversion, resource generation and UI acceptance.

Local, offline checks: python -B scripts/check_chinese_convert_runtime.py --static
Deployment host only: python -B scripts/check_chinese_convert_runtime.py <candidate-image>
The offline mode checks bilingual resources, twelve TSVs/manifest and full licence;
it does not prove conversion correctness or execute MySQL/Flyway/HTTP acceptance.
"""
from __future__ import print_function
import hashlib
import json
import os
import re
import sys
import uuid
import xml.etree.ElementTree as ET
from check_admin_statistics_runtime import run
from check_pinyin_runtime import check_statistics, check_tool_metadata as _check_tool_metadata


ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CODE = 'chinese_convert'
CATALOG = 'tools?categoryCode=TEXT&pageSize=100'
ADMIN_PATH = 'admin/tools/' + CODE
ADMIN_FILTER = 'admin/tools?categoryCode=TEXT&keyword=' + CODE + '&status=ENABLED&pageSize=100'
SEED_PATH = os.path.join(ROOT, 'server/src/main/resources/db/migration/V57__seed_chinese_convert_tool.sql')
CONTRACT = {
    'code': CODE, 'name': u'简繁互转',
    'description': u'按词组转换简繁体，支持台湾与香港字形和地区用词。',
    'categoryCode': 'TEXT', 'mode': 'LOCAL', 'icon': CODE, 'sortOrder': 100,
    'requiresLogin': False, 'featured': False, 'status': 'ENABLED', 'version': 1,
    'keywords': [u'简繁', u'简体', u'繁体', u'台湾', u'香港', 'OpenCC', u'汉字'],
}
ENGLISH = {
    'name': 'Chinese Script Converter',
    'description': 'Convert Chinese by phrase, with Taiwan and Hong Kong forms and regional vocabulary.',
}
OPENCC_COMMIT = '556ed22496d650bd0b13b6c163be9814637970ae'
OPENCC_LICENSE_SHA256 = 'b534e465949558eec2597b04f5092b5e161236a68dfbfd04d547592ac3964308'
DICTIONARIES = (
    'STCharacters', 'STPhrases', 'TSCharacters', 'TSPhrases',
    'TWVariants', 'TWVariantsRevPhrases', 'HKVariants', 'HKVariantsRevPhrases',
    'TWPhrases', 'TWPhrasesRev', 'TWVariantsRev', 'HKVariantsRev',
)


def check_tool_metadata(check, row, label, expected=None, public=False):
    """Reuse V56's public/admin DTO assertions with this tool's explicit contract."""
    _check_tool_metadata(check, row, label, CONTRACT if expected is None else expected, public)


def check_seed_metadata(check):
    check(os.path.isfile(SEED_PATH), 'V57 catalog seed exists')
    with open(SEED_PATH, 'rb') as stream:
        seed = stream.read().decode('utf-8')
    body = '\n'.join(line for line in seed.splitlines() if not line.lstrip().startswith('--'))
    columns = re.search(r'\A\s*INSERT INTO tool_definition\s*\(([^)]+)\)\s*VALUES\s*\(', body)
    check(columns is not None and [item.strip() for item in columns.group(1).split(',')] == [
        'tool_code', 'name', 'description', 'category_id', 'icon', 'keywords_json',
        'mode', 'requires_login', 'status', 'version', 'sort_order', 'is_featured'],
        'V57 only seeds the existing catalog columns')
    strings = [item.replace("''", "'") for item in re.findall(r"'((?:''|[^'])*)'", body)]
    check(strings == [CODE, CONTRACT['name'], CONTRACT['description'], 'TEXT', CODE]
          + CONTRACT['keywords'] + ['LOCAL', 'ENABLED'], 'V57 display metadata matches the fixed contract')
    check(re.search(r"'LOCAL'\s*,\s*0\s*,\s*'ENABLED'\s*,\s*1\s*,\s*100\s*,\s*0\s*\)", body) is not None,
          'V57 defaults match mode, login, status, version, order and feature flags')
    check(body.count(';') == 1 and re.search(
        r'ON DUPLICATE KEY UPDATE\s+tool_code\s*=\s*tool_code\s*;\s*\Z', body) is not None,
        'V57 replay uses the established no-op duplicate-key update')


def check_android_metadata(check):
    for locale, expected in (('values', CONTRACT), ('values-en', ENGLISH)):
        path = os.path.join(ROOT, 'android/app/src/main/res', locale, 'strings.xml')
        resources = ET.parse(path).getroot()
        for field in ('name', 'description'):
            nodes = resources.findall("string[@name='chinese_convert_%s']" % field)
            check(len(nodes) == 1 and nodes[0].text == expected[field],
                  'Android ' + locale + ' Chinese converter ' + field + ' matches the contract')
        arrays = resources.findall("string-array[@name='chinese_convert_keywords']")
        check(len(arrays) == 1, 'Android ' + locale + ' has one Chinese converter keyword array')
        keywords = [item.text for item in arrays[0].findall('item')]
        check(len(keywords) == 7 and len(set(keywords)) == 7
              and all(item and item.strip() for item in keywords),
              'Android ' + locale + ' Chinese converter has seven distinct nonempty keywords')
        if locale == 'values':
            check(keywords == CONTRACT['keywords'], 'Android Chinese keywords match the ordered contract')
        summary = resources.findall("string[@name='licenses_info_body']")
        check(len(summary) == 1 and 'OpenCC' in (summary[0].text or ''),
              'Android ' + locale + ' licence summary lists OpenCC')


def check_bundled_tables(check):
    directory = os.path.join(ROOT, 'android/app/src/main/assets/opencc')
    path = os.path.join(directory, 'manifest.json')
    check(os.path.isfile(path), 'bundled OpenCC manifest exists')
    with open(path, 'rb') as stream:
        manifest = json.loads(stream.read().decode('utf-8'))
    check(manifest.get('project') == 'BYVoid/OpenCC' and manifest.get('version') == '1.1.9'
          and manifest.get('commit') == OPENCC_COMMIT and manifest.get('license') == 'Apache-2.0',
          'OpenCC manifest records the fixed upstream revision and licence')
    tables = manifest.get('dictionaries', {})
    check(set(tables) == set(DICTIONARIES), 'OpenCC manifest declares exactly twelve dictionaries')
    check(set(name for name in os.listdir(directory) if name.endswith('.tsv'))
          == set(name + '.tsv' for name in DICTIONARIES), 'all twelve OpenCC TSVs are shipped')
    for name in DICTIONARIES:
        with open(os.path.join(directory, name + '.tsv'), 'rb') as stream:
            raw = stream.read()
        metadata = tables[name]
        check(len(raw) > 0 and len(raw) == metadata.get('bytes')
              and hashlib.sha256(raw).hexdigest() == metadata.get('sha256'),
              'OpenCC ' + name + ' bytes and SHA-256 match the manifest')
        check(raw.endswith(b'\n') and b'\r' not in raw and not raw.startswith(b'\xef\xbb\xbf'),
              'OpenCC ' + name + ' uses UTF-8 without BOM and LF endings')
        rows = [line.split('\t') for line in raw.decode('utf-8').splitlines()]
        check(len(rows) == metadata.get('entries') and all(
            len(row) == 2 and row[0].strip() and row[1].strip() for row in rows),
            'OpenCC ' + name + ' entry count and two-column format match the manifest')
        check(len(set(row[0] for row in rows)) == len(rows), 'OpenCC ' + name + ' keys are unique')
    licence = os.path.join(ROOT, 'android/app/src/main/assets/licenses/opencc.txt')
    check(os.path.isfile(licence), 'complete OpenCC licence file is shipped')
    with open(licence, 'rb') as stream:
        raw = stream.read()
    text = raw.decode('utf-8')
    check('OpenCC' in text and OPENCC_COMMIT in text, 'OpenCC licence identifies its source revision')
    start = raw.find(b'Apache License\n')
    check(start >= 0 and hashlib.sha256(raw[start:]).hexdigest() == OPENCC_LICENSE_SHA256,
          'OpenCC full Apache-2.0 licence matches the pinned upstream text')


def check_static(check):
    check_seed_metadata(check)
    check_bundled_tables(check)
    check_android_metadata(check)


def verify(check, request, sql, admin):
    check_static(check)
    check(sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version='57' AND success=1") == '1',
          'V57 applied')
    with open(SEED_PATH, 'rb') as stream:
        seed = stream.read().decode('utf-8')
    defaults = dict(CONTRACT, configJson='{}')

    def call(path, payload=None, token=None, method=None, status=200, code=0):
        actual, body, cache = request(path, payload, token=token, method=method)
        check(actual == status and body.get('code') == code, (method or 'GET') + ' ' + path)
        if path.startswith('admin/statistics') and status == 200:
            check(cache == 'no-store', 'statistics responses forbid caching')
        return body.get('data')

    def records(path=CATALOG, token=None):
        page = call(path, token=token)
        rows = page['records']
        codes = [row['code'] for row in rows]
        check(len(codes) == len(set(codes)) == page['total'], 'filtered catalog is complete and unique')
        check([(row['sortOrder'], row['code']) for row in rows]
              == sorted((row['sortOrder'], row['code']) for row in rows), 'catalog uses stable order')
        return dict((row['code'], row) for row in rows)

    def statistics(days=7):
        return call('admin/statistics?days=' + str(days), token=admin)

    def usage_counts():
        return tuple(sql("SELECT COALESCE(SUM(use_count),0) FROM %s WHERE tool_code='%s'" % (table, CODE))
                     for table in ('tool_usage_daily', 'tool_usage_stat'))

    def replay(label):
        query = "SELECT * FROM tool_definition WHERE tool_code='%s'" % CODE
        snapshot = sql(query)
        sql(seed)
        sql(seed)
        check(sql(query) == snapshot, label)
        check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='%s'" % CODE) == '1',
              'seed replay keeps one Chinese converter row')

    catalog = records()
    check(CODE in catalog, 'public TEXT catalog includes the Chinese converter')
    check_tool_metadata(check, catalog[CODE], 'public Chinese converter', public=True)
    check_tool_metadata(check, call(ADMIN_PATH, token=admin), 'admin Chinese converter', defaults)
    check_tool_metadata(check, records(ADMIN_FILTER, admin)[CODE], 'admin Chinese converter filter', defaults)
    categories = call('admin/tools/categories', token=admin)
    check(any(row['code'] == 'TEXT' and row['enabled'] for row in categories), 'admin editor offers TEXT')

    # A fresh ordinary user is removed before collecting tool-statistics baselines.
    account = {'email': 'chinese-' + uuid.uuid4().hex + '@example.invalid',
               'password': uuid.uuid4().hex, 'nickname': 'catalog-acceptance'}
    identity = call('auth/register', account, method='POST', status=201)
    user = identity['accessToken']
    try:
        probes = (('GET', None, ADMIN_PATH), ('GET', None, ADMIN_FILTER),
                  ('GET', None, 'admin/tools/categories'), ('GET', None, 'admin/statistics?days=7'),
                  ('POST', defaults, 'admin/tools'), ('PUT', defaults, ADMIN_PATH),
                  ('PATCH', {'status': 'MAINTENANCE'}, ADMIN_PATH + '/status'),
                  ('DELETE', None, ADMIN_PATH))
        snapshot = sql("SELECT * FROM tool_definition WHERE tool_code='%s'" % CODE)
        for token, status, error in ((None, 401, 10002), (user, 403, 10005)):
            for method, payload, path in probes:
                call(path, payload, token=token, method=method, status=status, code=error)
        check(sql("SELECT * FROM tool_definition WHERE tool_code='%s'" % CODE) == snapshot,
              'rejected admin operations preserve the seeded row')
    finally:
        call('auth/me', {'email': account['email'], 'password': account['password']},
             token=user, method='DELETE')

    # Exercise successful creation without deleting/recreating the unique seeded code.
    probe = dict(defaults, code=CODE + '_admin_check')
    probe_path = 'admin/tools/' + probe['code']
    call('admin/tools', dict(probe, categoryCode='NOPE'), token=admin, method='POST', status=404, code=30005)
    check_tool_metadata(check, call('admin/tools', probe, token=admin, method='POST', status=201),
                        'admin-created Chinese converter probe', probe)
    check_tool_metadata(check, call(probe_path, token=admin), 'persisted admin-created probe', probe)
    call('admin/tools', defaults, token=admin, method='POST', status=409, code=30004)
    call(probe_path, token=admin, method='DELETE')
    call(probe_path, token=admin, status=404, code=10006)
    replay('fresh seed replay preserves default metadata and timestamps')
    check(usage_counts() == ('0', '0'), 'new Chinese converter has no synthetic opens')
    before = dict((days, statistics(days)) for days in (7, 30, 90))
    for unused in range(2):
        call('tools/' + CODE + '/usage', method='POST')
    check(usage_counts() == ('2', '2'), 'Chinese converter increments daily and all-time counters twice')
    check(sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily WHERE tool_code='%s' "
              "AND usage_date=DATE(UTC_TIMESTAMP(3)+INTERVAL 8 HOUR)" % CODE) == '2',
          'Chinese converter daily opens use the Beijing date')
    popular = dict((row['code'], row) for row in call('tools/popular?limit=50'))
    check(CODE in popular and popular[CODE]['useCount'] == 2, 'Chinese converter participates in popularity')
    for days in (7, 30, 90):
        data = statistics(days)
        check_statistics(check, data, days)
        rank = next(row for row in data['ranking'] if row['code'] == CODE)
        check(rank['opens'] == 2 and rank['allTimeOpens'] == 2 and rank['name'] == CONTRACT['name']
              and rank['status'] == 'ENABLED', 'Chinese converter ranking for ' + str(days) + ' days')
        check(all(data['summary'][key] == before[days]['summary'][key] + 2
                  for key in ('allTimeOpens', 'windowOpens', 'todayOpens')),
              'Chinese converter contributes to ' + str(days) + '-day aggregate opens')
        for group, group_code in (('categories', 'TEXT'), ('modes', 'LOCAL')):
            old = next(row for row in before[days][group] if row['code'] == group_code)
            current = next(row for row in data[group] if row['code'] == group_code)
            check(current['opens'] == old['opens'] + 2 and current['count'] == old['count'],
                  'Chinese converter participates in ' + group + ' for ' + str(days) + ' days')

    edited = dict(defaults, name=u'简繁目录验收', description=u'后台编辑的功能说明。',
                  icon=CODE + '_admin_check', keywords=[u'验收', 'opencc-admin'], sortOrder=101,
                  version=2, featured=True, requiresLogin=True, configJson='{"acceptance":"chinese_convert"}')
    check_tool_metadata(check, call(ADMIN_PATH, edited, token=admin, method='PUT'), 'edited converter', edited)
    check_tool_metadata(check, call(ADMIN_PATH, token=admin), 'persisted converter edit', edited)
    check_tool_metadata(check, records()[CODE], 'public converter edit', edited, public=True)
    replay('seed replay preserves operator edits and configuration')
    call(ADMIN_PATH + '/status', {'status': 'INVALID'}, token=admin, method='PATCH', status=400, code=10001)
    call(ADMIN_PATH, dict(edited, configJson='[]'), token=admin, method='PUT', status=400, code=10001)
    check_tool_metadata(check, call(ADMIN_PATH, token=admin), 'invalid edits leave converter intact', edited)

    for state, counter in (('DISABLED', 'disabledTools'), ('MAINTENANCE', 'maintenanceTools')):
        expected = dict(edited, status=state)
        check_tool_metadata(check, call(ADMIN_PATH + '/status', {'status': state}, token=admin, method='PATCH'),
                            'admin converter ' + state, expected)
        check(CODE not in records(), state + ' converter hidden from public catalog')
        check(CODE not in [row['code'] for row in call('tools/popular?limit=50')],
              state + ' converter hidden from popularity')
        call('tools/' + CODE + '/usage', method='POST', status=404, code=10006)
        check(usage_counts() == ('2', '2'), state + ' converter rejects opens without changing counters')
        replay('seed replay preserves ' + state + ', operator edits and timestamps')
        state_filter = ADMIN_FILTER.replace('status=ENABLED', 'status=' + state)
        check_tool_metadata(check, records(state_filter, admin)[CODE], 'admin filtered ' + state, expected)
        data = statistics()
        rank = next(row for row in data['ranking'] if row['code'] == CODE)
        check(rank['status'] == state and rank['name'] == edited['name']
              and rank['opens'] == 2 and rank['allTimeOpens'] == 2, state + ' converter retains historical ranking')
        check(data['summary'][counter] == before[7]['summary'][counter] + 1
              and data['summary']['enabledTools'] == before[7]['summary']['enabledTools'] - 1,
              state + ' converter participates in state totals')

    call(ADMIN_PATH + '/status', {'status': 'ENABLED'}, token=admin, method='PATCH')
    check_tool_metadata(check, records()[CODE], 're-enabled converter retains edits', edited, public=True)
    check(CODE in [row['code'] for row in call('tools/popular?limit=50')], 're-enabled converter returns to popularity')
    check_tool_metadata(check, call(ADMIN_PATH, defaults, token=admin, method='PUT'), 'restored converter', defaults)
    call(ADMIN_PATH, token=admin, method='DELETE')
    check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='%s' AND deleted_at IS NOT NULL" % CODE) == '1',
          'admin deletion is a soft delete')
    replay('seed replay preserves soft deletion, catalog fields and timestamps')
    for method, payload, path in (('GET', None, ADMIN_PATH), ('PUT', defaults, ADMIN_PATH),
                                  ('PATCH', {'status': 'ENABLED'}, ADMIN_PATH + '/status'),
                                  ('DELETE', None, ADMIN_PATH), ('POST', None, 'tools/' + CODE + '/usage')):
        call(path, payload, token=admin if path.startswith('admin/') else None,
             method=method, status=404, code=10006)
    call('admin/tools', defaults, token=admin, method='POST', status=409, code=30004)
    check(CODE not in records(ADMIN_FILTER, admin) and CODE not in records(),
          'deleted converter absent from admin and public lists')
    check(CODE not in [row['code'] for row in call('tools/popular?limit=50')], 'deleted converter absent from popularity')
    check(usage_counts() == ('2', '2'), 'deleted converter retains both historical counters')
    for days in (7, 30, 90):
        data = statistics(days)
        check_statistics(check, data, days)
        old_summary = before[days]['summary']
        check(CODE not in [row['code'] for row in data['ranking']], 'deleted converter excluded from ranking')
        check(data['summary']['tools'] == old_summary['tools'] - 1
              and data['summary']['enabledTools'] == old_summary['enabledTools'] - 1
              and all(data['summary'][key] == old_summary[key] + 2
                      for key in ('allTimeOpens', 'windowOpens', 'todayOpens')),
              'soft deletion retains aggregate history for ' + str(days) + ' days')
        for group, group_code in (('categories', 'TEXT'), ('modes', 'LOCAL')):
            old = next(row for row in before[days][group] if row['code'] == group_code)
            current = next(row for row in data[group] if row['code'] == group_code)
            check(current['count'] == old['count'] - 1 and current['opens'] == old['opens'],
                  'deleted converter excluded from ' + group + ' distribution for ' + str(days) + ' days')


def main():
    if len(sys.argv) == 2 and sys.argv[1] == '--static':
        checks = [0]

        def check(condition, label):
            if not condition:
                raise AssertionError(label + ' (content omitted)')
            checks[0] += 1
            print('PASS', checks[0], label)

        check_static(check)
        print('Chinese converter offline acceptance:', checks[0], 'checks passed; no network, Docker or database access')
    elif len(sys.argv) == 2 and not sys.argv[1].startswith('-'):
        run(sys.argv[1], extra_checks=verify)
    else:
        raise SystemExit('Usage: python -B scripts/check_chinese_convert_runtime.py --static | <candidate-image>')


if __name__ == '__main__':
    main()
