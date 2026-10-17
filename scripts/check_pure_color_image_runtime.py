# -*- coding: utf-8 -*-
"""V60 pure_color_image catalog and isolated backend acceptance (Python 2/3 stdlib).

Offline: python -B scripts/check_pure_color_image_runtime.py --static
Deployment host: python -B scripts/check_pure_color_image_runtime.py <candidate-image>

The static mode checks V60, bilingual Android resources and placeholders, the
ToolDefinition contract, one bundled registration, one icon mapping and one
nonempty 24dp vector icon. The candidate mode reuses
check_admin_statistics_runtime.run: a fresh schema, restricted DB user and
isolated candidate container with finally cleanup. It does not deploy an image
or execute Android bitmap generation, transparency, save/share or device UI.

Source reviewed 2026-10-17 at ImageToolbox master commit
e0c91fa856886bd5465212b6605952207ca10ff6:
https://github.com/T8RIN/ImageToolbox/blob/e0c91fa856886bd5465212b6605952207ca10ff6/core/ui/src/main/kotlin/com/t8rin/imagetoolbox/core/ui/utils/ColorImageExporter.kt
https://github.com/T8RIN/ImageToolbox/blob/e0c91fa856886bd5465212b6605952207ca10ff6/core/ui/src/main/kotlin/com/t8rin/imagetoolbox/core/ui/widget/dialogs/ColorImageExportDialog.kt
The files establish 1..4096 edge validation, createBitmap/eraseColor filling,
PNG export, transparent preview and external save-location ideas. No upstream
code is copied and no dependency is added by this checker.
The script creates no temporary files; use -B to suppress import caches.
"""
from __future__ import print_function
import os
import re
import sys
import uuid
import xml.etree.ElementTree as ET

sys.dont_write_bytecode = True
from check_admin_statistics_runtime import run
from check_color_picker_runtime import check_catalog_page, check_statistics, check_tool_metadata, read_source


ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CODE = 'pure_color_image'
RESOURCE_PREFIX = 'pure_color_'
CATALOG = 'tools?categoryCode=IMAGE&pageSize=100'
ADMIN_PATH = 'admin/tools/' + CODE
ADMIN_FILTER = 'admin/tools?categoryCode=IMAGE&keyword=' + CODE + '&status=ENABLED&pageSize=100'
SEED_PATH = os.path.join(ROOT, 'server/src/main/resources/db/migration/V60__seed_pure_color_image_tool.sql')
JAVA_ROOT = os.path.join(ROOT, 'android/app/src/main/java')
RES_ROOT = os.path.join(ROOT, 'android/app/src/main/res')
FEATURE_ROOT = os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/feature/purecolor')
CONTRACT = {
    'code': CODE,
    'name': u'纯色图制作',
    'description': u'选择颜色与尺寸，生成透明或纯色 PNG，并保存或分享。',
    'categoryCode': 'IMAGE',
    'mode': 'LOCAL',
    'icon': CODE,
    'sortOrder': 107,
    'requiresLogin': False,
    'featured': False,
    'status': 'ENABLED',
    'version': 1,
    'keywords': [u'纯色', u'纯色图', u'颜色', u'背景图', 'PNG', u'生成', u'图片'],
}
ENGLISH = {
    'name': 'Pure Colour Image',
    'description': 'Create a solid or transparent PNG at a custom size, then save or share it.',
    'keywords': ['solid color', 'pure color', 'background', 'image', 'png', 'generate', 'blank'],
}


def read_optional(path):
    try:
        return read_source(path)
    except (IOError, OSError):
        return None


def check_seed_metadata(check):
    source = read_optional(SEED_PATH)
    check(source is not None, 'V60 catalog seed exists')
    if source is None:
        return
    body = '\n'.join(line for line in source.splitlines() if not line.lstrip().startswith('--'))
    columns = re.search(r'\A\s*INSERT INTO tool_definition\s*\(([^)]+)\)\s*VALUES\s*\(', body)
    check(columns is not None and [item.strip() for item in columns.group(1).split(',')] == [
        'tool_code', 'name', 'description', 'category_id', 'icon', 'keywords_json',
        'mode', 'requires_login', 'status', 'version', 'sort_order', 'is_featured'],
        'V60 only seeds existing catalog columns')
    strings = [item.replace("''", "'") for item in re.findall(r"'((?:''|[^'])*)'", body)]
    check(strings == [CODE, CONTRACT['name'], CONTRACT['description'], 'IMAGE', CODE]
          + CONTRACT['keywords'] + ['LOCAL', 'ENABLED'],
          'V60 display metadata and ordered Chinese keywords match')
    check(re.search(r"\(SELECT\s+id\s+FROM\s+tool_category\s+WHERE\s+code\s*=\s*'IMAGE'\)", body) is not None,
          'V60 category resolves the IMAGE category by code')
    check(re.search(r"'LOCAL'\s*,\s*0\s*,\s*'ENABLED'\s*,\s*1\s*,\s*107\s*,\s*0\s*\)", body) is not None,
          'V60 mode, login, status, version, sort and featured defaults match')
    check(body.count('INSERT INTO tool_definition') == 1
          and body.count(';') == 1
          and re.search(r'ON DUPLICATE KEY UPDATE\s+tool_code\s*=\s*tool_code\s*;\s*\Z', body) is not None,
          'V60 is one metadata insert with a no-op duplicate-key replay')


def android_string_references():
    references = set()
    if not os.path.isdir(FEATURE_ROOT):
        return None
    for current, unused, names in os.walk(FEATURE_ROOT):
        for name in names:
            if not name.endswith('.kt'):
                continue
            source = read_optional(os.path.join(current, name)) or ''
            references.update(re.findall(r'R\.string\.(pure_color_[A-Za-z0-9_]+)', source))
    return references


def check_android_metadata(check):
    references = android_string_references()
    check(references is not None and {'pure_color_name', 'pure_color_description'}.issubset(references),
          'Android pure_color feature references bilingual metadata resources')
    translations = {}
    for locale, expected in (('values', CONTRACT), ('values-en', ENGLISH)):
        path = os.path.join(RES_ROOT, locale, 'strings.xml')
        source = read_optional(path)
        check(source is not None, 'Android ' + locale + ' pure_color resources exist')
        if source is None:
            continue
        try:
            resources = ET.fromstring(source.encode('utf-8'))
        except ET.ParseError:
            check(False, 'Android ' + locale + ' strings.xml parses')
            continue
        check(True, 'Android ' + locale + ' strings.xml parses')
        strings = [node for node in resources.findall('string')
                   if node.get('name', '').startswith(RESOURCE_PREFIX)]
        names = [node.get('name') for node in strings]
        check(len(names) == len(set(names)) and all(''.join(node.itertext()).strip() for node in strings),
              'Android ' + locale + ' pure_color strings are unique and nonempty')
        translations[locale] = dict((node.get('name'), ''.join(node.itertext())) for node in strings)
        for field in ('name', 'description'):
            check(translations[locale].get(RESOURCE_PREFIX + field) == expected[field],
                  'Android ' + locale + ' pure_color ' + field + ' matches')
        arrays = resources.findall("string-array[@name='pure_color_keywords']")
        check(len(arrays) == 1, 'Android ' + locale + ' pure_color keywords exist once')
        if len(arrays) == 1:
            keywords = [(node.text or '').strip() for node in arrays[0].findall('item')]
            check(keywords == expected['keywords'] and len(set(keywords)) == len(keywords)
                  and all(keywords), 'Android ' + locale + ' ordered pure_color keywords match')
        if references is not None:
            missing = sorted(references - set(translations[locale]))
            check(not missing, 'Android ' + locale + ' covers every referenced pure_color string')
    if 'values' in translations and 'values-en' in translations:
        check(set(translations['values']) == set(translations['values-en']),
              'Android pure_color Chinese and English string keys match')

        def placeholders(value):
            return sorted(re.findall(r'%(?:\d+\$)?[a-zA-Z%]', value))

        check(all(placeholders(value) == placeholders(translations['values-en'][name])
                  for name, value in translations['values'].items()),
              'Android pure_color bilingual format placeholders match')


def definition_owners():
    owners = []
    if not os.path.isdir(JAVA_ROOT):
        return owners
    for current, unused, names in os.walk(JAVA_ROOT):
        for name in names:
            if name.endswith('ToolDefinition.kt'):
                path = os.path.join(current, name)
                source = read_optional(path) or ''
                count = len(re.findall(r'\bcode\s*=\s*"pure_color_image"', source))
                owners.extend([os.path.normpath(path)] * count)
    return owners


def check_android_registration(check):
    owners = definition_owners()
    check(len(owners) == 1, 'Android pure_color_image code has exactly one ToolDefinition owner')
    if not owners:
        return
    definition_path = owners[0]
    definition = read_optional(definition_path)
    check(definition is not None, 'Android pure_color_image ToolDefinition is readable')
    if definition is None:
        return
    for field, pattern in (
        ('code', r'code\s*=\s*"pure_color_image"'),
        ('name', r'name\s*=\s*resources\.getString\(R\.string\.pure_color_name\)'),
        ('description', r'description\s*=\s*resources\.getString\(R\.string\.pure_color_description\)'),
        ('category', r'category\s*=\s*ToolCategory\.IMAGE'),
        ('mode', r'mode\s*=\s*ToolMode\.LOCAL'),
        ('icon', r'icon\s*=\s*"pure_color_image"'),
        ('keywords', r'keywords\s*=\s*resources\.getStringArray\(R\.array\.pure_color_keywords\)\.toList\(\)'),
        ('sort', r'sortOrder\s*=\s*107\b'),
        ('featured', r'isFeatured\s*=\s*false\b'),
    ):
        check(len(re.findall(pattern, definition)) == 1,
              'Android pure_color_image ToolDefinition ' + field)
    defaults = read_optional(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/core/model/ToolMetadata.kt')) or ''
    for field, default in (('requiresLogin', 'false'), ('status', 'ToolStatus.ENABLED'), ('version', '1')):
        value = re.search(r'\b' + field + r'\s*=\s*([^,\n]+)', definition)
        agrees = value.group(1).strip() == default if value else re.search(
            r'val\s+' + field + r'\s*:\s*\w+\s*=\s*' + re.escape(default) + r'\s*,', defaults) is not None
        check(agrees, 'Android pure_color_image ' + field + ' agrees with catalog defaults')
    check(re.search(r'override\s+val\s+requiredPermissions\s*:\s*List<String>\s*=\s*emptyList\(\)', definition) is not None,
          'Android pure_color_image declares no extra runtime permissions')

    container_path = os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/ToolboxAppContainer.kt')
    container = read_optional(container_path)
    check(container is not None, 'Android bundled ToolRegistry source exists')
    if container is not None:
        registry = re.search(r'private fun bundledRegistry\b[\s\S]+?(?=private val registryStore)', container)
        class_name = os.path.splitext(os.path.basename(definition_path))[0]
        check(registry is not None and 'ToolRegistry(' in registry.group(0)
              and len(re.findall(r'\b' + re.escape(class_name) + r'\s*\(', registry.group(0))) == 1,
              'Android bundled ToolRegistry registers pure_color_image exactly once')

    icons_path = os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/feature/home/ui/HomePresentation.kt')
    icons = read_optional(icons_path)
    check(icons is not None and len(re.findall(r'"pure_color_image"\s*->', icons)) == 1
          and re.search(r'"pure_color_image"\s*->\s*R\.drawable\.ic_pure_color_image\b', icons) is not None,
          'Android pure_color_image has one explicit icon mapping')
    icon_path = os.path.join(RES_ROOT, 'drawable/ic_pure_color_image.xml')
    icon_source = read_optional(icon_path)
    check(icon_source is not None, 'Android pure_color_image vector icon exists')
    if icon_source is not None:
        try:
            icon = ET.fromstring(icon_source.encode('utf-8'))
        except ET.ParseError:
            icon = None
        android = '{http://schemas.android.com/apk/res/android}'
        check(icon is not None and icon.tag == 'vector' and icon.get(android + 'width') == '24dp'
              and icon.get(android + 'height') == '24dp'
              and icon.get(android + 'viewportWidth') == '24'
              and icon.get(android + 'viewportHeight') == '24'
              and any(node.get(android + 'pathData', '').strip() for node in icon.iter('path')),
              'Android pure_color_image ships a nonempty 24dp vector icon')


def check_static(check):
    check_seed_metadata(check)
    check_android_metadata(check)
    check_android_registration(check)


def offline_acceptance():
    checks = [0]
    failures = []

    def check(condition, label):
        checks[0] += 1
        if condition:
            print('PASS', checks[0], label)
        else:
            failures.append(label)
            print('FAIL', checks[0], label)

    check_static(check)
    print('Pure Colour Image offline static acceptance:', checks[0] - len(failures), '/', checks[0],
          'checks passed;', len(failures), 'failed; no network, Docker or database access;'
          ' Android/runtime/device/deployment acceptance not executed')
    if failures:
        raise SystemExit(1)


def verify(check, request, sql, admin):
    """Mutations here are exclusively through the isolated harness callbacks."""
    check_static(check)
    source = read_source(SEED_PATH)
    defaults = dict(CONTRACT, configJson='{}')

    def call(path, payload=None, token=None, method=None, status=200, code=0):
        actual, body, cache = request(path, payload, token=token, method=method)
        check(actual == status and body.get('code') == code, (method or 'GET') + ' ' + path)
        if path.startswith('admin/statistics') and status == 200:
            check(cache == 'no-store', 'statistics responses forbid caching')
        return body.get('data')

    def records(path=CATALOG, token=None):
        return check_catalog_page(check, call(path, token=token), path)

    def usage_counts():
        return tuple(sql("SELECT COALESCE(SUM(use_count),0) FROM %s WHERE tool_code='%s'" % (table, CODE))
                     for table in ('tool_usage_daily', 'tool_usage_stat'))

    check(sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version='60' AND success=1") == '1',
          'V60 applied')
    check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='%s'" % CODE) == '1',
          'V60 creates one pure_color_image row')
    check(CODE in records(), 'public IMAGE catalog includes pure_color_image')
    check_tool_metadata(check, records()[CODE], 'public pure_color_image', expected=defaults, public=True)
    check_tool_metadata(check, call(ADMIN_PATH, token=admin), 'admin pure_color_image detail', defaults)
    check_tool_metadata(check, records(ADMIN_FILTER, admin)[CODE], 'admin pure_color_image list', defaults)
    categories = call('admin/tools/categories', token=admin)
    check(len([row for row in categories if row['code'] == 'IMAGE' and row['enabled']]) == 1,
          'admin editor offers the enabled IMAGE category')

    edited = dict(defaults, name=u'纯色图制作验收', description=u'后台编辑的纯色图说明。',
                  icon=CODE + '_admin_check', keywords=[u'验收', 'pure-color-admin'], sortOrder=108,
                  version=2, featured=True, requiresLogin=True,
                  configJson='{"acceptance":"pure_color_image"}')
    check_tool_metadata(check, call(ADMIN_PATH, edited, token=admin, method='PUT'),
                        'edited pure_color_image', edited)
    queries = ["SELECT * FROM tool_definition WHERE tool_code='%s'" % CODE,
               "SELECT * FROM flyway_schema_history WHERE version='60'",
               "SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily WHERE tool_code='%s'" % CODE,
               "SELECT COALESCE(SUM(use_count),0) FROM tool_usage_stat WHERE tool_code='%s'" % CODE]
    snapshot = [sql(query) for query in queries]
    sql(source)
    sql(source)
    check([sql(query) for query in queries] == snapshot,
          'V60 replay preserves operator edits, configuration, timestamps and usage')
    check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='%s'" % CODE) == '1',
          'V60 replay keeps one pure_color_image row, including deleted rows')
    check_tool_metadata(check, call(ADMIN_PATH, token=admin), 'replayed pure_color_image', edited)
    check_tool_metadata(check, call(ADMIN_PATH, defaults, token=admin, method='PUT'),
                        'restored pure_color_image', defaults)

    check(usage_counts() == ('0', '0'), 'fresh pure_color_image has no synthetic opens')
    for unused in range(2):
        call('tools/' + CODE + '/usage', method='POST')
    check(usage_counts() == ('2', '2'), 'pure_color_image increments daily and all-time counters twice')
    check(sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily WHERE tool_code='%s' "
              "AND usage_date=DATE(UTC_TIMESTAMP(3)+INTERVAL 8 HOUR)" % CODE) == '2',
          'pure_color_image daily opens use the Beijing date')
    popular = call('tools/popular?limit=50')
    popular_map = dict((row['code'], row) for row in popular)
    row = popular_map.get(CODE)
    check(row is not None and row['useCount'] == 2
          and all(row[key] == defaults[key] for key in ('name', 'description', 'icon')),
          'pure_color_image popularity metadata and counters match')
    data = call('admin/statistics?days=7', token=admin)
    check_statistics(check, data, 7)
    ranks = [row for row in data['ranking'] if row['code'] == CODE]
    check(len(ranks) == 1 and ranks[0]['opens'] == ranks[0]['allTimeOpens'] == 2
          and ranks[0]['name'] == defaults['name'] and ranks[0]['status'] == 'ENABLED',
          'pure_color_image participates in 7-day ranking')


def main():
    if len(sys.argv) == 2 and sys.argv[1] == '--static':
        offline_acceptance()
    elif len(sys.argv) == 2 and not sys.argv[1].startswith('-'):
        run(sys.argv[1], extra_checks=verify)
    else:
        raise SystemExit('Usage: python -B scripts/check_pure_color_image_runtime.py --static | <candidate-image>')


if __name__ == '__main__':
    main()
