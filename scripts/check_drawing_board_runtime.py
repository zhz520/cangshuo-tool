# -*- coding: utf-8 -*-
"""V76 drawing_board static and isolated backend acceptance (Python 2/3).

Offline: python -B scripts/check_drawing_board_runtime.py --static
Deployment host: python -B scripts/check_drawing_board_runtime.py <candidate-image>
"""
from __future__ import print_function
import os
import re
import sys
import xml.etree.ElementTree as ET

sys.dont_write_bytecode = True
from check_admin_statistics_runtime import run
from check_color_picker_runtime import check_catalog_page, check_statistics, check_tool_metadata, read_source


ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CODE = 'drawing_board'
CATALOG = 'tools?categoryCode=LIFE&pageSize=100'
ADMIN_PATH = 'admin/tools/' + CODE
ADMIN_FILTER = 'admin/tools?categoryCode=LIFE&keyword=drawing_board&status=ENABLED&pageSize=100'
SEED_PATH = os.path.join(ROOT, 'server/src/main/resources/db/migration/V76__seed_drawing_board_tool.sql')
JAVA_ROOT = os.path.join(ROOT, 'android/app/src/main/java')
RES_ROOT = os.path.join(ROOT, 'android/app/src/main/res')
FEATURE_ROOT = os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/feature/drawboard')
RESOURCE_NAME = re.compile(r'^drawing_board_[A-Za-z0-9_]+$')
KEYWORDS_ZH = [
    u'画板', u'画画', u'涂鸦', u'手绘', u'绘图', u'画笔', u'橡皮擦',
    'drawing', 'draw', 'sketch', 'canvas', 'paint',
]
KEYWORDS_EN = [
    'drawing board', 'draw', 'sketch', 'doodle', 'canvas', 'paint', 'brush', 'eraser',
    u'画板', u'画画', u'涂鸦', u'画笔',
]
CONTRACT = {
    'code': CODE,
    'name': u'画板',
    'description': u'在空白画布上自由绘制，支持多色画笔、粗细调节、橡皮擦、撤销重做和 PNG 保存分享。',
    'categoryCode': 'LIFE',
    'mode': 'LOCAL',
    'icon': CODE,
    'sortOrder': 628,
    'requiresLogin': False,
    'featured': False,
    'status': 'ENABLED',
    'version': 1,
    'keywords': KEYWORDS_ZH,
}
ENGLISH = {
    'name': 'Drawing Board',
    'description': 'Sketch freely with multiple colors, adjustable brush sizes, an eraser, undo and redo, plus PNG save and share.',
    'keywords': KEYWORDS_EN,
}
DOMAIN_FEATURES = (
    ('stroke budget', 'const val MAX_STROKES = 80'),
    ('point budget per stroke', 'const val MAX_POINTS_PER_STROKE = 1_000'),
    ('total point budget', 'const val MAX_TOTAL_POINTS = 8_000'),
    ('brush size bounds', 'const val MIN_BRUSH_DP = 1'),
    ('export dimensions', 'const val EXPORT_WIDTH = 1_200'),
    ('file byte budget', 'const val MAX_FILE_BYTES = 12L * 1024L * 1024L'),
    ('undo budget', 'const val MAX_UNDO = 30'),
    ('point normalization', 'point.x.coerceIn(0f, 1f)'),
    ('document validation', 'fun validDocument(document: DrawingDocument): Boolean'),
    ('portable corner clip', 'val clipRadiusFraction: Float = 0f'),
    ('stable png filename', 'SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US)'),
)
UI_FEATURES = (
    ('managed palette', 'BrushColor(0xFF111827.toInt()'),
    ('eraser filter', 'FilterChip('),
    ('size slider', 'Slider('),
    ('undo and redo', 'drawing_board_redo'),
    ('continuous pointer gesture', 'awaitEachGesture'),
    ('bounded sampling', 'DrawingBoardPolicy.MAX_POINTS_PER_STROKE'),
    ('smooth quadratic stroke', 'quadraticTo('),
    ('PNG save', 'ActivityResultContracts.CreateDocument("image/png")'),
    ('share chooser', 'Intent.ACTION_SEND'),
    ('accessible colors', 'this.selected = !state.eraser'),
    ('accessible grid mode', 'R.string.drawing_board_grid_cell'),
    ('accessible slider', 'R.string.drawing_board_brush_accessibility'),
    ('wrapped control spacing', 'verticalArrangement = Arrangement.spacedBy(8.dp)'),
    ('cancelled gesture cleanup', 'onStroke(current.toList(), strokeWidth, strokeArgb, clipRadiusNow)'),
)


def check_seed(check):
    source = read_source(SEED_PATH)
    check(source is not None, 'V76 catalog seed exists')
    migration_dir = os.path.dirname(SEED_PATH)
    v76_files = sorted(name for name in os.listdir(migration_dir) if name.startswith('V76__'))
    check(v76_files == [os.path.basename(SEED_PATH)], 'V76 has exactly one migration')
    body = '\n'.join(line for line in source.splitlines() if not line.lstrip().startswith('--'))
    columns = re.search(r'\A\s*INSERT INTO tool_definition\s*\(([^)]+)\)\s*VALUES\s*\(', body)
    check(columns is not None and [item.strip() for item in columns.group(1).split(',')] == [
        'tool_code', 'name', 'description', 'category_id', 'icon', 'keywords_json',
        'mode', 'requires_login', 'status', 'version', 'sort_order', 'is_featured'],
        'V76 only seeds existing tool_definition columns')
    strings = [item.replace("''", "'") for item in re.findall(r"'((?:''|[^'])*)'", body)]
    check(strings == [CODE, CONTRACT['name'], CONTRACT['description'], 'LIFE', CODE]
          + KEYWORDS_ZH + ['LOCAL', 'ENABLED'],
          'V76 display metadata and ordered Chinese keywords match')
    check(re.search(r"\(SELECT\s+id\s+FROM\s+tool_category\s+WHERE\s+code\s*=\s*'LIFE'\)", body)
          is not None, 'V76 category resolves LIFE by code')
    check(re.search(r"'LOCAL'\s*,\s*0\s*,\s*'ENABLED'\s*,\s*1\s*,\s*628\s*,\s*0\s*\)", body)
          is not None, 'V76 mode, login, status, version, sort and featured defaults match')
    check(body.count('INSERT INTO tool_definition') == 1 and body.count(';') == 1,
          'V76 is one metadata insert')
    check(re.search(r'ON DUPLICATE KEY UPDATE\s+tool_code\s*=\s*tool_code\s*;\s*\Z', body) is not None,
          'V76 replay preserves operator edits')


def placeholders(value):
    return sorted(re.findall(r'%(?:\d+\$)?[a-zA-Z%]', value))


def check_android_resources(check):
    feature = ''
    for current, unused, names in os.walk(FEATURE_ROOT):
        for name in names:
            if name.endswith('.kt'):
                feature += '\n' + read_source(os.path.join(current, name))
    references = set(re.findall(r'R\.string\.(drawing_board_[A-Za-z0-9_]+)', feature))
    arrays = set(re.findall(r'R\.array\.(drawing_board_[A-Za-z0-9_]+)', feature))
    check({'drawing_board_name', 'drawing_board_description'}.issubset(references),
          'Android drawing board references metadata resources')
    check(arrays == {'drawing_board_keywords'}, 'Android drawing board references its keyword array once')
    translations = {}
    for locale, expected, keywords in (('values', CONTRACT, KEYWORDS_ZH),
                                       ('values-en', ENGLISH, KEYWORDS_EN)):
        source = read_source(os.path.join(RES_ROOT, locale, 'strings.xml'))
        resources = ET.fromstring(source.encode('utf-8'))
        strings = [node for node in resources.findall('string')
                   if RESOURCE_NAME.match(node.get('name', ''))]
        names = [node.get('name') for node in strings]
        translations[locale] = dict((node.get('name'), ''.join(node.itertext())) for node in strings)
        check(len(names) == len(set(names)) and all(''.join(node.itertext()).strip() for node in strings),
              'Android ' + locale + ' drawing board strings are unique and nonempty')
        check(set(translations[locale]) == references,
              'Android ' + locale + ' contains exactly the referenced drawing board strings')
        check(translations[locale].get('drawing_board_name') == expected['name']
              and translations[locale].get('drawing_board_description') == expected['description'],
              'Android ' + locale + ' drawing board metadata matches')
        keyword_nodes = resources.findall("string-array[@name='drawing_board_keywords']")
        actual = [(node.text or '').strip() for node in keyword_nodes[0].findall('item')] if keyword_nodes else []
        check(len(keyword_nodes) == 1 and actual == keywords and len(set(actual)) == len(actual),
              'Android ' + locale + ' ordered drawing board keywords match')
    check(set(translations['values']) == set(translations['values-en']),
          'Android drawing board bilingual string keys match')
    check(all(placeholders(value) == placeholders(translations['values-en'][name])
              for name, value in translations['values'].items()),
          'Android drawing board bilingual placeholders match')


def check_registration(check):
    definition_path = os.path.join(FEATURE_ROOT, 'DrawingBoardToolDefinition.kt')
    definition = read_source(definition_path)
    for label, token in (
        ('code', 'code = "drawing_board"'),
        ('name resource', 'R.string.drawing_board_name'),
        ('description resource', 'R.string.drawing_board_description'),
        ('category', 'ToolCategory.LIFE'),
        ('mode', 'ToolMode.LOCAL'),
        ('icon', 'icon = "drawing_board"'),
        ('keywords', 'R.array.drawing_board_keywords'),
        ('sort', 'sortOrder = 628'),
        ('featured', 'isFeatured = false'),
    ):
        check(token in definition, 'Android drawing board ToolDefinition ' + label)
    check('requiredPermissions: List<String> = emptyList()' in definition,
          'Android drawing board declares no runtime permissions')
    owners = []
    for current, unused, names in os.walk(JAVA_ROOT):
        for name in names:
            if name.endswith('ToolDefinition.kt'):
                path = os.path.join(current, name)
                owners.extend([os.path.normpath(path)] * len(re.findall(
                    r'\bcode\s*=\s*"drawing_board"', read_source(path))))
    check(owners == [os.path.normpath(definition_path)],
          'Android drawing board has exactly one ToolDefinition owner')
    container = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/ToolboxAppContainer.kt'))
    registry = re.search(r'private fun bundledRegistry\b[\s\S]+?(?=private val registryStore)', container)
    check(registry is not None and len(re.findall(r'\bDrawingBoardToolDefinition\s*\(', registry.group(0))) == 1,
          'Android bundled ToolRegistry registers drawing_board exactly once')
    icons = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/feature/home/ui/HomePresentation.kt'))
    check(re.search(r'"drawing_board"\s*->\s*R\.drawable\.ic_drawing_board\b', icons) is not None,
          'Android drawing board has one explicit icon mapping')
    icon_path = os.path.join(RES_ROOT, 'drawable/ic_drawing_board.xml')
    icon = ET.parse(icon_path).getroot()
    android = '{http://schemas.android.com/apk/res/android}'
    check(icon.tag == 'vector' and icon.get(android + 'width') == '24dp'
          and icon.get(android + 'height') == '24dp' and any(
              node.get(android + 'pathData', '').strip() for node in icon.iter('path')),
          'Android drawing board ships a nonempty 24dp vector icon')
    paths = read_source(os.path.join(RES_ROOT, 'xml/file_paths.xml'))
    check('path="drawing_board_share/"' in paths, 'Android drawing board has a scoped share cache')


def check_implementation(check):
    domain = read_source(os.path.join(FEATURE_ROOT, 'domain/DrawingBoardModels.kt'))
    for label, token in DOMAIN_FEATURES:
        check(token in domain, 'drawing board domain provides ' + label)
    repository = read_source(os.path.join(FEATURE_ROOT, 'data/LocalDrawingBoardRepository.kt'))
    for label, token in (
        ('MediaStore export', 'MediaStore.Images.Media.RELATIVE_PATH'),
        ('FileProvider sharing', 'FileProvider.getUriForFile'),
        ('bounded output', 'class LimitedOutputStream'),
        ('serialized native work', 'Mutex()'),
        ('bitmap cleanup', 'bitmap.recycle()'),
        ('pre-rendered output', 'val rendered = renderTemp(document)'),
        ('safe commit rollback', 'if (!committed && !isDocument)'),
        ('cache pruning', 'private fun pruneShareCache()'),
    ('rounded PNG clipping', 'addRoundRect('),
    ):
        check(token in repository, 'drawing board repository provides ' + label)
    view_model = read_source(os.path.join(FEATURE_ROOT, 'ui/DrawingBoardViewModel.kt'))
    check('saved["drawDocument"] = encode(document)' in view_model
          and 'undo.addLast(document)' in view_model and 'redo.addLast(document)' in view_model,
          'drawing board persists current document and maintains undo/redo')
    check('fun onLeavePage()' in view_model and 'activeJob?.cancel()' in view_model,
          'drawing board cancels pending export when leaving the page')
    screen = read_source(os.path.join(FEATURE_ROOT, 'ui/DrawingBoardScreen.kt'))
    for label, token in UI_FEATURES:
        check(token in screen, 'drawing board UI provides ' + label)
    check('mutableStateOf(IntSize.Zero)' in screen and '.onSizeChanged { canvasSize = it }' in screen,
          'drawing board recomposes after canvas measurement')
    check('current.size < DrawingBoardPolicy.MAX_POINTS_PER_STROKE' in screen,
          'drawing board bounds live gesture sampling')


def check_candidate_isolation(check):
    script = read_source(os.path.join(ROOT, 'scripts/check_drawing_board_runtime.py'))
    harness = read_source(os.path.join(ROOT, 'scripts/check_admin_statistics_runtime.py'))
    check('run(sys.argv[1], extra_checks=verify, strict_isolation=True)' in script,
          'drawing board candidate opts into credential-stripped isolation')
    check("'redis-server', '--save', '', '--appendonly', 'no'" in harness
          and 'temporary_network = ' in harness, 'strict candidate uses disposable services and network')


def check_static(check):
    check_seed(check)
    check_android_resources(check)
    check_registration(check)
    check_implementation(check)
    check_candidate_isolation(check)


def offline_acceptance():
    checks = [0]

    def check(condition, label):
        if not condition:
            raise AssertionError(label)
        checks[0] += 1
        print('PASS', checks[0], label)

    check_static(check)
    print('Drawing board offline static acceptance:', checks[0], '/', checks[0],
          'checks passed; 0 failed; no network, Docker, database or Android runtime')


def verify(check, request, sql, admin):
    check_static(check)
    defaults = dict(CONTRACT, configJson='{}')

    def call(path, payload=None, token=None, method=None, status=200, code=0):
        actual, body, cache = request(path, payload, token=token, method=method)
        check(actual == status and body.get('code') == code, (method or 'GET') + ' ' + path)
        if path.startswith('admin/statistics') and status == 200:
            check(cache == 'no-store', 'statistics responses forbid caching')
        return body.get('data')

    def records(path=CATALOG, token=None):
        return check_catalog_page(check, call(path, token=token), path)

    check(sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version='76' AND success=1") == '1',
          'V76 applied')
    check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='%s'" % CODE) == '1',
          'V76 creates one drawing board row')
    check(CODE in records(), 'public LIFE catalog includes drawing_board')
    check_tool_metadata(check, records()[CODE], 'public drawing board', expected=defaults, public=True)
    detail = call(ADMIN_PATH, token=admin)
    listing = records(ADMIN_FILTER, admin)[CODE]
    check_tool_metadata(check, detail, 'admin drawing board detail', defaults)
    check_tool_metadata(check, listing, 'admin drawing board list', defaults)
    check(detail == listing, 'admin drawing board detail and list agree')
    categories = call('admin/tools/categories', token=admin)
    check(len([row for row in categories if row['code'] == 'LIFE' and row['enabled']]) == 1,
          'admin editor offers enabled LIFE category')

    edited = dict(defaults,
                  name=u'画板验收', description=u'后台编辑的画板说明。',
                  icon=CODE + '_admin_check', keywords=[u'验收', 'drawing-board-admin'],
                  sortOrder=629, version=2, featured=True, requiresLogin=True,
                  configJson='{"acceptance":"drawing_board"}')
    check_tool_metadata(check, call(ADMIN_PATH, edited, token=admin, method='PUT'),
                        'edited drawing board metadata', edited)
    for unused in range(2):
        call('tools/' + CODE + '/usage', method='POST')
    queries = [
        "SELECT * FROM tool_definition WHERE tool_code='%s'" % CODE,
        "SELECT * FROM flyway_schema_history WHERE version='76'",
        "SELECT * FROM tool_usage_daily WHERE tool_code='%s' ORDER BY usage_date" % CODE,
        "SELECT * FROM tool_usage_stat WHERE tool_code='%s'" % CODE,
    ]
    snapshot = [sql(query) for query in queries]
    source = read_source(SEED_PATH)
    sql(source)
    sql(source)
    check([sql(query) for query in queries] == snapshot,
          'V76 replay preserves operator edits, configuration, timestamps and usage')
    check_tool_metadata(check, call(ADMIN_PATH, token=admin),
                        'seed replay preserves edited drawing board', edited)
    check_tool_metadata(check, call(ADMIN_PATH, defaults, token=admin, method='PUT'),
                        'restored drawing board metadata', defaults)
    daily = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily WHERE tool_code='%s'" % CODE)
    total = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_stat WHERE tool_code='%s'" % CODE)
    check(daily == total == '2', 'drawing board increments daily and all-time usage')
    check(sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily WHERE tool_code='%s' "
              "AND usage_date=DATE(UTC_TIMESTAMP(3)+INTERVAL 8 HOUR)" % CODE) == '2',
          'drawing board daily opens use the Beijing date')
    popular = dict((row['code'], row) for row in call('tools/popular?limit=50')).get(CODE)
    check(popular is not None and popular['useCount'] == 2
          and all(popular[key] == CONTRACT[key] for key in ('name', 'description', 'icon')),
          'drawing board participates in popularity')
    for days in (7, 30, 90):
        data = call('admin/statistics?days=' + str(days), token=admin)
        check_statistics(check, data, days)
        for group, group_code in (('categories', 'LIFE'), ('modes', 'LOCAL')):
            groups = [row for row in data[group] if row['code'] == group_code]
            check(len(groups) == 1 and groups[0]['count'] >= 1 and groups[0]['opens'] >= 2,
                  str(days) + '-day ' + group + ' includes drawing board usage')
        ranks = [row for row in data['ranking'] if row['code'] == CODE]
        check(len(ranks) == 1 and ranks[0]['opens'] == ranks[0]['allTimeOpens'] == 2,
              'drawing board participates in ' + str(days) + '-day ranking')


def main():
    if len(sys.argv) == 2 and sys.argv[1] == '--static':
        offline_acceptance()
    elif len(sys.argv) == 2 and not sys.argv[1].startswith('-'):
        run(sys.argv[1], extra_checks=verify, strict_isolation=True)
    else:
        raise SystemExit('Usage: python -B scripts/check_drawing_board_runtime.py --static | <candidate-image>')


if __name__ == '__main__':
    main()
