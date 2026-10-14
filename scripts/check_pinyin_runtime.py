# -*- coding: utf-8 -*-
"""V56 pinyin catalog/admin/statistics acceptance on an isolated candidate.

Python 2/3 stdlib. Reuses check_admin_statistics_runtime.run for temporary
database/user/container setup and finally cleanup. Only the isolated schema
receives catalog edits, seed replays and synthetic usage; no execution API is used.
Checks the bundled MIT pinyin table, Chinese/English Android resources, the fixed
metadata contract, admin read/write guards, disable/maintenance/re-enable/soft-delete,
replay preservation, daily/all-time counters, public popularity and 7/30/90-day
aggregate statistics.
Usage on the deployment host: python scripts/check_pinyin_runtime.py <candidate-image>
"""
from __future__ import print_function
from datetime import datetime, timedelta
import json
import os
import sys
import xml.etree.ElementTree as ET
from check_admin_statistics_runtime import run


ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CODE = 'pinyin'
CATALOG = 'tools?categoryCode=TEXT&pageSize=100'
ADMIN_PATH = 'admin/tools/' + CODE
CONTRACT = {
    'code': CODE, 'name': u'汉字转拼音',
    'description': u'支持声调符号、声调数字、无声调、首字母与多音字查询，离线字库覆盖四万余个汉字，并识别常用多音字词组。',
    'categoryCode': 'TEXT', 'mode': 'LOCAL', 'icon': CODE, 'sortOrder': 95,
    'requiresLogin': False, 'featured': False, 'status': 'ENABLED', 'version': 1,
    'keywords': [u'拼音', u'汉字转拼音', 'pinyin', u'声调', u'首字母', u'多音字', 'pinyin converter'],
}


def check_bundled_table(check):
    """The conversion runs offline, so the shipped table and its licence are acceptance items."""
    table = os.path.join(ROOT, 'android/app/src/main/assets/pinyin/readings.tsv')
    licence = os.path.join(ROOT, 'android/app/src/main/assets/licenses/pinyin-data.txt')
    check(os.path.isfile(table) and os.path.getsize(table) > 400000, 'bundled pinyin table shipped')
    rows = 0
    with open(table, 'rb') as stream:
        for line in stream:
            if line.strip():
                rows += 1
    check(rows > 40000, 'bundled pinyin table covers more than 40000 code points')
    with open(licence, 'rb') as stream:
        text = stream.read().decode('utf-8')
    check('MIT' in text and 'mozillazg' in text, 'pinyin-data MIT licence text shipped')
    for locale in ('values', 'values-en'):
        path = os.path.join(ROOT, 'android/app/src/main/res', locale, 'strings.xml')
        body = ET.parse(path).getroot()
        nodes = body.findall("string[@name='licenses_info_body']")
        check(len(nodes) == 1 and 'pinyin-data' in (nodes[0].text or ''),
              'Android ' + locale + ' licence summary lists pinyin-data')


def check_android_metadata(check):
    resources = {}
    for locale in ('values', 'values-en'):
        path = os.path.join(ROOT, 'android/app/src/main/res', locale, 'strings.xml')
        resources[locale] = ET.parse(path).getroot()
        for field in ('name', 'description'):
            nodes = resources[locale].findall("string[@name='pinyin_%s']" % field)
            check(len(nodes) == 1 and bool((nodes[0].text or '').strip()),
                  'Android ' + locale + ' has one nonempty pinyin ' + field)
            text = nodes[0].text
            check(text == CONTRACT[field] if locale == 'values' else text != CONTRACT[field],
                  'Android ' + locale + ' pinyin ' + field + ' matches its locale contract')
    arrays = resources['values'].findall("string-array[@name='pinyin_keywords']")
    check(len(arrays) == 1, 'Android Chinese pinyin keywords resource exists once')
    check([item.text for item in arrays[0].findall('item')] == CONTRACT['keywords'],
          'Android Chinese keywords match the ordered pinyin contract')
    english = resources['values-en'].findall("string-array[@name='pinyin_keywords']")
    check(len(english) <= 1 and (len(english) == 1 or arrays[0].get('translatable') == 'false'),
          'Android English keywords are explicit or use the shared nontranslatable array')
    effective = english[0] if english else arrays[0]
    aliases = [item.text for item in effective.findall('item')]
    check(len(aliases) == len(CONTRACT['keywords']) and all(item and item.strip() for item in aliases),
          'Android English pinyin keywords keep the Chinese array length')
    check(set(('pinyin', 'tone', 'initials', 'polyphone')).issubset(set(aliases))
          and any(item in aliases for item in ('hanzi to pinyin', 'chinese pinyin')),
          'Android English effective keywords cover pinyin, tones, initials and polyphones')


def check_tool_metadata(check, row, label, expected=None, public=False):
    """Public ToolResponse uses isFeatured; AdminToolResponse uses featured/configJson."""
    expected = CONTRACT if expected is None else expected
    display = ('name', 'description', 'keywords')
    registration = ('code', 'categoryCode', 'mode', 'icon', 'sortOrder', 'requiresLogin', 'status', 'version')
    check(all(row.get(key) == expected[key] for key in display), label + ' display metadata')
    check(all(row.get(key) == expected[key] for key in registration), label + ' registration')
    feature_key = 'isFeatured' if public else 'featured'
    check(row.get(feature_key) is expected['featured'], label + ' ' + feature_key)
    if public:
        check(not any(key in row for key in ('featured', 'id', 'categoryName', 'configJson', 'updatedAt')),
              label + ' public DTO fields')
    elif 'configJson' in expected:
        check(json.loads(row['configJson']) == json.loads(expected['configJson']), label + ' configuration')


def check_statistics(check, data, days):
    label = str(days) + '-day statistics'
    check(data['days'] == days and data['timezone'] == 'Asia/Shanghai' and len(data['daily']) == days,
          label + ' window and timezone')
    start = datetime.strptime(data['from'], '%Y-%m-%d')
    dates = [(start + timedelta(days=offset)).strftime('%Y-%m-%d') for offset in range(days)]
    check([row['date'] for row in data['daily']] == dates and data['to'] == dates[-1],
          label + ' contiguous dates')
    summary = data['summary']
    check(sum(row['opens'] for row in data['daily']) == summary['windowOpens']
          and data['daily'][-1]['opens'] == summary['todayOpens']
          and all(row['opens'] >= 0 for row in data['daily'])
          and summary['allTimeOpens'] >= summary['windowOpens'] >= summary['todayOpens'] >= 0,
          label + ' daily and all-time totals are consistent')


def verify(check, request, sql, admin):
    check_bundled_table(check)
    check_android_metadata(check)
    check(sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version='56' AND success=1") == '1',
          'V56 applied')
    with open(os.path.join(ROOT, 'server/src/main/resources/db/migration/V56__seed_pinyin_tool.sql'), 'rb') as stream:
        seed = stream.read().decode('utf-8')

    def call(path, payload=None, token=None, method=None, status=200, code=0):
        actual, body, cache = request(path, payload, token=token, method=method)
        check(actual == status and body.get('code') == code, (method or 'GET') + ' ' + path)
        if path.startswith('admin/statistics') and status == 200:
            check(cache == 'no-store', 'statistics responses forbid caching')
        return body.get('data')

    def records(path=CATALOG, token=None):
        return dict((row['code'], row) for row in call(path, token=token)['records'])

    def statistics(days=7):
        return call('admin/statistics?days=' + str(days), token=admin)

    def usage_counts():
        return tuple(sql("SELECT COALESCE(SUM(use_count),0) FROM %s WHERE tool_code='pinyin'" % table)
                     for table in ('tool_usage_daily', 'tool_usage_stat'))

    def replay(label):
        snapshot = sql("SELECT * FROM tool_definition WHERE tool_code='pinyin'")
        sql(seed)
        sql(seed)
        check(sql("SELECT * FROM tool_definition WHERE tool_code='pinyin'") == snapshot, label)
        check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='pinyin'") == '1',
              'seed replay keeps exactly one pinyin row')

    catalog = records()
    check(CODE in catalog, 'public TEXT catalog includes pinyin')
    check_tool_metadata(check, catalog[CODE], 'public pinyin', public=True)
    defaults = dict(CONTRACT, configJson='{}')
    for method, payload, path in (('GET', None, ADMIN_PATH), ('PUT', defaults, ADMIN_PATH),
                                  ('PATCH', {'status': 'MAINTENANCE'}, ADMIN_PATH + '/status'),
                                  ('DELETE', None, ADMIN_PATH)):
        call(path, payload, method=method, status=401, code=10002)
    check_tool_metadata(check, call(ADMIN_PATH, token=admin), 'admin pinyin', defaults)
    categories = call('admin/tools/categories', token=admin)
    check(any(row['code'] == 'TEXT' and row['enabled'] for row in categories),
          'admin editor offers TEXT category')
    filtered = 'admin/tools?categoryCode=TEXT&keyword=pinyin&status=ENABLED&pageSize=100'
    check(CODE in records(filtered, admin), 'admin filters find the enabled pinyin tool')
    replay('fresh seed replay preserves default metadata and timestamps')
    check(usage_counts() == ('0', '0'), 'new pinyin tool has no synthetic usage yet')
    before = dict((days, statistics(days)) for days in (7, 30, 90))
    for unused in range(2):
        call('tools/' + CODE + '/usage', method='POST')
    check(usage_counts() == ('2', '2'), 'daily and all-time pinyin counters each increment twice')
    check(sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily WHERE tool_code='pinyin' "
              "AND usage_date=DATE(UTC_TIMESTAMP(3)+INTERVAL 8 HOUR)") == '2',
          'pinyin daily usage uses Beijing date')
    popular = dict((row['code'], row) for row in call('tools/popular?limit=50'))
    check(CODE in popular and popular[CODE]['useCount'] == 2, 'public popular list includes pinyin usage')
    for days in (7, 30, 90):
        data = statistics(days)
        check_statistics(check, data, days)
        ranking = dict((row['code'], row) for row in data['ranking'])
        check(CODE in ranking and ranking[CODE]['opens'] == 2 and ranking[CODE]['allTimeOpens'] == 2
              and ranking[CODE]['name'] == CONTRACT['name'] and ranking[CODE]['status'] == 'ENABLED',
              'pinyin daily and all-time ranking for ' + str(days) + ' days')
        check(all(data['summary'][key] == before[days]['summary'][key] + 2
                  for key in ('allTimeOpens', 'windowOpens', 'todayOpens')),
              'pinyin usage participates in aggregate totals for ' + str(days) + ' days')
        for group, group_code in (('categories', 'TEXT'), ('modes', 'LOCAL')):
            old = next(row for row in before[days][group] if row['code'] == group_code)
            current = next(row for row in data[group] if row['code'] == group_code)
            check(current['opens'] == old['opens'] + 2 and current['count'] == old['count'],
                  'pinyin usage participates in ' + group + ' for ' + str(days) + ' days')

    edited = dict(defaults, name=u'拼音目录验收', description=u'后台编辑的功能说明。',
                  icon='pinyin_admin_check', keywords=[u'验收', 'pinyin-admin'], sortOrder=96,
                  version=2, featured=True, requiresLogin=True, configJson='{"acceptance":"pinyin"}')
    public_edit = dict((key, edited[key]) for key in CONTRACT)
    check_tool_metadata(check, call(ADMIN_PATH, edited, token=admin, method='PUT'), 'edited pinyin', edited)
    check_tool_metadata(check, call(ADMIN_PATH, token=admin), 'persisted pinyin edit', edited)
    check_tool_metadata(check, records()[CODE], 'public pinyin edit', public_edit, public=True)
    replay('seed replay preserves operator edits and configuration')
    call(ADMIN_PATH + '/status', {'status': 'INVALID'}, token=admin, method='PATCH', status=400, code=10001)
    call(ADMIN_PATH, dict(edited, configJson='[]'), token=admin, method='PUT', status=400, code=10001)
    check_tool_metadata(check, call(ADMIN_PATH, token=admin), 'invalid edits leave pinyin intact', edited)

    for state, counter in (('DISABLED', 'disabledTools'), ('MAINTENANCE', 'maintenanceTools')):
        state_metadata = dict(edited, status=state)
        check_tool_metadata(check, call(ADMIN_PATH + '/status', {'status': state}, token=admin, method='PATCH'),
                            'admin pinyin ' + state, state_metadata)
        check(CODE not in records(), state + ' pinyin hidden from public catalog')
        check(CODE not in [row['code'] for row in call('tools/popular?limit=50')],
              state + ' pinyin hidden from popularity')
        call('tools/' + CODE + '/usage', method='POST', status=404, code=10006)
        check(usage_counts() == ('2', '2'), state + ' pinyin rejects usage without changing either counter')
        replay('seed replay preserves ' + state + ' state and operator edits')
        check_tool_metadata(check, call(ADMIN_PATH, token=admin), 'persisted pinyin ' + state, state_metadata)
        state_filter = 'admin/tools?categoryCode=TEXT&keyword=pinyin&status=' + state + '&pageSize=100'
        check(CODE in records(state_filter, admin), 'admin can filter ' + state + ' pinyin')
        data = statistics()
        rank = next(row for row in data['ranking'] if row['code'] == CODE)
        check(rank['status'] == state and rank['opens'] == 2 and rank['allTimeOpens'] == 2,
              state + ' pinyin retains historical ranking')
        check(data['summary'][counter] == before[7]['summary'][counter] + 1
              and data['summary']['enabledTools'] == before[7]['summary']['enabledTools'] - 1,
              state + ' pinyin participates in tool state statistics')

    call(ADMIN_PATH + '/status', {'status': 'ENABLED'}, token=admin, method='PATCH')
    check_tool_metadata(check, records()[CODE], 're-enabled pinyin retains edits', public_edit, public=True)
    check(CODE in [row['code'] for row in call('tools/popular?limit=50')], 're-enabled pinyin returns to popularity')
    check_tool_metadata(check, call(ADMIN_PATH, defaults, token=admin, method='PUT'), 'restored pinyin', defaults)
    call(ADMIN_PATH, token=admin, method='DELETE')
    check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='pinyin' AND deleted_at IS NOT NULL") == '1',
          'admin deletion is a soft delete')
    replay('seed replay preserves soft deletion and all catalog fields')
    call(ADMIN_PATH, token=admin, status=404, code=10006)
    call(ADMIN_PATH, defaults, token=admin, method='PUT', status=404, code=10006)
    call(ADMIN_PATH + '/status', {'status': 'ENABLED'}, token=admin, method='PATCH', status=404, code=10006)
    call('admin/tools', defaults, token=admin, method='POST', status=409, code=30004)
    check(CODE not in records(filtered, admin) and CODE not in records(),
          'deleted pinyin absent from admin and public lists')
    check(CODE not in [row['code'] for row in call('tools/popular?limit=50')],
          'deleted pinyin absent from popularity')
    call('tools/' + CODE + '/usage', method='POST', status=404, code=10006)
    check(usage_counts() == ('2', '2'), 'deleted pinyin keeps history and rejects new usage')
    data = statistics()
    check_statistics(check, data, 7)
    check(CODE not in [row['code'] for row in data['ranking']], 'deleted pinyin excluded from tool ranking')
    check(data['summary']['tools'] == before[7]['summary']['tools'] - 1
          and data['summary']['enabledTools'] == before[7]['summary']['enabledTools'] - 1
          and data['summary']['allTimeOpens'] == before[7]['summary']['allTimeOpens'] + 2
          and data['summary']['windowOpens'] == before[7]['summary']['windowOpens'] + 2
          and data['summary']['todayOpens'] == before[7]['summary']['todayOpens'] + 2,
          'soft deletion excludes the tool but retains aggregate historical opens')
    for group, group_code in (('categories', 'TEXT'), ('modes', 'LOCAL')):
        old = next(row for row in before[7][group] if row['code'] == group_code)
        current = next(row for row in data[group] if row['code'] == group_code)
        check(current['count'] == old['count'] - 1 and current['opens'] == old['opens'],
              'deleted pinyin excluded from ' + group + ' distribution')


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('Usage: python scripts/check_pinyin_runtime.py <candidate-image>')
    run(sys.argv[1], extra_checks=verify)
