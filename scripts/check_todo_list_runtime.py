# -*- coding: utf-8 -*-
"""V77 todo_list static and isolated backend acceptance (Python 2/3).

Offline: python -B scripts/check_todo_list_runtime.py --static
Deployment host: python -B scripts/check_todo_list_runtime.py <candidate-image>
"""
from __future__ import print_function
import json
import os
import re
import sys
import xml.etree.ElementTree as ET

sys.dont_write_bytecode = True
from check_admin_statistics_runtime import run
from check_color_picker_runtime import check_catalog_page, check_statistics, check_tool_metadata, read_source


ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CODE = 'todo_list'
CATALOG = 'tools?categoryCode=LIFE&pageSize=100'
ADMIN_PATH = 'admin/tools/' + CODE
ADMIN_FILTER = 'admin/tools?categoryCode=LIFE&keyword=todo_list&status=ENABLED&pageSize=100'
SEED_PATH = os.path.join(ROOT, 'server/src/main/resources/db/migration/V77__seed_todo_list_tool.sql')
JAVA_ROOT = os.path.join(ROOT, 'android/app/src/main/java')
RES_ROOT = os.path.join(ROOT, 'android/app/src/main/res')
FEATURE_ROOT = os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/feature/todo')
RESOURCE_NAME = re.compile(r'^todo_[A-Za-z0-9_]+$')
KEYWORDS_ZH = [
    u'待办', u'待办事项', u'任务', u'清单', u'计划', u'截止日期', u'优先级',
    'todo', 'task', 'checklist', 'planner',
]
KEYWORDS_EN = [
    'tasks', 'todo', 'checklist', 'planner', 'to-do', 'due date', 'priority',
    u'待办', u'任务', u'清单', u'计划',
]
CONTRACT = {
    'code': CODE,
    'name': u'待办事项',
    'description': u'在本机管理待办任务，支持完成状态、优先级、截止日期、搜索、筛选、排序和清除已完成事项。',
    'categoryCode': 'LIFE',
    'mode': 'LOCAL',
    'icon': CODE,
    'sortOrder': 629,
    'requiresLogin': False,
    'featured': False,
    'status': 'ENABLED',
    'version': 1,
    'keywords': KEYWORDS_ZH,
}
ENGLISH = {
    'name': 'Tasks',
    'description': 'Manage local tasks with completion, priority, due dates, search, filters, sorting and completed-task cleanup.',
    'keywords': KEYWORDS_EN,
}
DOMAIN_FEATURES = (
    ('task limit', 'const val MAX_TASKS = 500'),
    ('title budget', 'const val MAX_TITLE_CODE_POINTS = 120'),
    ('notes budget', 'const val MAX_NOTES_CODE_POINTS = 2_000'),
    ('title validation', 'fun validateTitle(title: String): TodoError?'),
    ('notes validation', 'fun validateNotes(notes: String): TodoError?'),
    ('title normalization', 'fun normalizeTitle(title: String): String'),
    ('search and status filtering', 'fun filterAndSort('),
    ('active-first ordering', 'TodoSort.ACTIVE_FIRST -> filtered.sortedWith(activeFirst.then(newest))'),
    ('due-date ordering', 'TodoSort.DUE_DATE -> filtered.sortedWith(activeFirst.then(due)'),
    ('priority ordering', 'TodoSort.PRIORITY -> filtered.sortedWith(activeFirst.then(priority)'),
    ('newest ordering', 'TodoSort.CREATED_DESC -> filtered.sortedWith(newest)'),
    ('task statistics', 'fun stats(items: List<TodoItem>, todayEpochDay: Long): TodoStats'),
    ('create and update use cases', 'class TodoUseCases'),
)
UI_FEATURES = (
    ('search field', 'R.string.todo_search'),
    ('status filters', 'TodoFilter.COMPLETED'),
    ('four sort controls', 'TodoSort.CREATED_DESC'),
    ('add and edit dialog', 'TodoEditorDialog('),
    ('date picker', 'DatePickerDialog('),
    ('delete confirmation', 'R.string.todo_delete_title'),
    ('completed cleanup confirmation', 'R.string.todo_clear_title'),
    ('empty and loading states', 'ToolboxLoadingState('),
    ('scrollable editor', '.weight(1f, fill = false)'),
    ('keyboard-safe dialog', 'Modifier.fillMaxSize().safeDrawingPadding().imePadding()'),
    ('adaptive statistics', 'Stat(stringResource(R.string.todo_stat_active), state.stats.active, Modifier.weight(1f))'),
    ('wrap-safe controls', 'FlowRow('),
    ('completion checkbox', 'onCheckedChange = { onToggle() }'),
)


def check_seed(check):
    source = read_source(SEED_PATH)
    check(source is not None, 'V77 catalog seed exists')
    migration_dir = os.path.dirname(SEED_PATH)
    v77_files = sorted(name for name in os.listdir(migration_dir) if name.startswith('V77__'))
    check(v77_files == [os.path.basename(SEED_PATH)], 'V77 has exactly one migration')
    body = '\n'.join(line for line in source.splitlines() if not line.lstrip().startswith('--'))
    columns = re.search(r'\A\s*INSERT INTO tool_definition\s*\(([^)]+)\)\s*VALUES\s*\(', body)
    check(columns is not None and [item.strip() for item in columns.group(1).split(',')] == [
        'tool_code', 'name', 'description', 'category_id', 'icon', 'keywords_json',
        'mode', 'requires_login', 'status', 'version', 'sort_order', 'is_featured'],
        'V77 only seeds existing tool_definition columns')
    strings = [item.replace("''", "'") for item in re.findall(r"'((?:''|[^'])*)'", body)]
    check(strings == [CODE, CONTRACT['name'], CONTRACT['description'], 'LIFE', CODE]
          + KEYWORDS_ZH + ['LOCAL', 'ENABLED'],
          'V77 display metadata and ordered Chinese keywords match')
    check(re.search(r"\(SELECT\s+id\s+FROM\s+tool_category\s+WHERE\s+code\s*=\s*'LIFE'\)", body)
          is not None, 'V77 category resolves LIFE by code')
    check(re.search(r"'LOCAL'\s*,\s*0\s*,\s*'ENABLED'\s*,\s*1\s*,\s*629\s*,\s*0\s*\)", body)
          is not None, 'V77 mode, login, status, version, sort and featured defaults match')
    check(body.count('INSERT INTO tool_definition') == 1 and body.count(';') == 1,
          'V77 is one metadata insert')
    check(re.search(r'ON DUPLICATE KEY UPDATE\s+tool_code\s*=\s*tool_code\s*;\s*\Z', body) is not None,
          'V77 replay preserves operator edits')


def placeholders(value):
    return sorted(re.findall(r'%(?:\d+\$)?[a-zA-Z%]', value))


def check_android_resources(check):
    feature = ''
    for current, unused, names in os.walk(FEATURE_ROOT):
        for name in names:
            if name.endswith('.kt'):
                feature += '\n' + read_source(os.path.join(current, name))
    references = set(re.findall(r'R\.string\.(todo_[A-Za-z0-9_]+)', feature))
    arrays = set(re.findall(r'R\.array\.(todo_[A-Za-z0-9_]+)', feature))
    check({'todo_name', 'todo_description'}.issubset(references),
          'Android todo references metadata resources')
    check(arrays == {'todo_keywords'}, 'Android todo references its keyword array once')
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
              'Android ' + locale + ' todo strings are unique and nonempty')
        check(set(translations[locale]) == references,
              'Android ' + locale + ' contains exactly the referenced todo strings')
        check(translations[locale].get('todo_name') == expected['name']
              and translations[locale].get('todo_description') == expected['description'],
              'Android ' + locale + ' todo metadata matches')
        keyword_nodes = resources.findall("string-array[@name='todo_keywords']")
        actual = [(node.text or '').strip() for node in keyword_nodes[0].findall('item')] if keyword_nodes else []
        check(len(keyword_nodes) == 1 and actual == keywords and len(set(actual)) == len(actual),
              'Android ' + locale + ' ordered todo keywords match')
    check(set(translations['values']) == set(translations['values-en']),
          'Android todo bilingual string keys match')
    check(all(placeholders(value) == placeholders(translations['values-en'][name])
              for name, value in translations['values'].items()),
          'Android todo bilingual placeholders match')


def check_registration(check):
    definition_path = os.path.join(FEATURE_ROOT, 'TodoToolDefinition.kt')
    definition = read_source(definition_path)
    for label, token in (
        ('code', 'code = "todo_list"'),
        ('name resource', 'R.string.todo_name'),
        ('description resource', 'R.string.todo_description'),
        ('category', 'ToolCategory.LIFE'),
        ('mode', 'ToolMode.LOCAL'),
        ('icon', 'icon = "todo_list"'),
        ('keywords', 'R.array.todo_keywords'),
        ('sort', 'sortOrder = 629'),
        ('featured', 'isFeatured = false'),
    ):
        check(token in definition, 'Android todo ToolDefinition ' + label)
    check('requiredPermissions: List<String> = emptyList()' in definition,
          'Android todo declares no runtime permissions')
    owners = []
    for current, unused, names in os.walk(JAVA_ROOT):
        for name in names:
            if name.endswith('ToolDefinition.kt'):
                path = os.path.join(current, name)
                owners.extend([os.path.normpath(path)] * len(re.findall(
                    r'\bcode\s*=\s*"todo_list"', read_source(path))))
    check(owners == [os.path.normpath(definition_path)],
          'Android todo has exactly one ToolDefinition owner')
    container = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/ToolboxAppContainer.kt'))
    registry = re.search(r'private fun bundledRegistry\b[\s\S]+?(?=private val registryStore)', container)
    check(registry is not None and len(re.findall(r'\bTodoToolDefinition\s*\(', registry.group(0))) == 1,
          'Android bundled ToolRegistry registers todo_list exactly once')
    icons = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/feature/home/ui/HomePresentation.kt'))
    check(re.search(r'"todo_list"\s*->\s*R\.drawable\.ic_todo_list\b', icons) is not None,
          'Android todo has one explicit icon mapping')
    icon_path = os.path.join(RES_ROOT, 'drawable/ic_todo_list.xml')
    icon = ET.parse(icon_path).getroot()
    android = '{http://schemas.android.com/apk/res/android}'
    check(icon.tag == 'vector' and icon.get(android + 'width') == '24dp'
          and icon.get(android + 'height') == '24dp' and any(
              node.get(android + 'pathData', '').strip() for node in icon.iter('path')),
          'Android todo ships a nonempty 24dp vector icon')
    manifest = read_source(os.path.join(ROOT, 'android/app/src/main/AndroidManifest.xml'))
    check('android:windowSoftInputMode="adjustResize"' in manifest,
          'Android activity resizes above the software keyboard')


def check_database(check):
    database = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/core/database/ToolboxDatabase.kt'))
    check('TodoTaskEntity::class' in database and 'version = 8' in database
          and 'abstract fun todoTaskDao(): TodoTaskDao' in database,
          'Room database exports todo table at schema version 8')
    migration = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/core/database/ToolboxDatabaseMigrations.kt'))
    check('val MIGRATION_7_8 = object : Migration(7, 8)' in migration,
          'Room declares the 7 to 8 migration')
    for token in ('CREATE TABLE IF NOT EXISTS todo_task', 'due_date INTEGER', 'completed_at INTEGER',
                  'index_todo_task_completed', 'index_todo_task_due_date', 'index_todo_task_created_at'):
        check(token in migration, 'Room migration contains ' + token)
    schema_path = os.path.join(ROOT, 'android/app/schemas/com.cangshuo.toolbox.core.database.ToolboxDatabase/8.json')
    schema = json.loads(read_source(schema_path))
    entity = [row for row in schema['database']['entities'] if row['tableName'] == 'todo_task']
    check(schema['database']['version'] == 8 and len(entity) == 1,
          'Room schema 8 contains one todo table')
    entity = entity[0]
    check(entity['primaryKey'] == {'autoGenerate': True, 'columnNames': ['id']},
          'Room todo uses an auto-generated id')
    check(set(row['columnName'] for row in entity['fields']) == {
        'id', 'title', 'notes', 'due_date', 'priority', 'completed', 'completed_at', 'created_at', 'updated_at',
    }, 'Room todo columns match the migration')
    check(set(row['name'] for row in entity['indices']) == {
        'index_todo_task_completed', 'index_todo_task_due_date', 'index_todo_task_created_at',
    }, 'Room todo indexes match the migration')
    dao = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/core/database/TodoTaskDao.kt'))
    check('fun observeAll(): Flow<List<TodoTaskEntity>>' in dao
          and 'suspend fun deleteCompleted(): Int' in dao,
          'Room dao exposes observable tasks and completed cleanup')


def check_implementation(check):
    domain = read_source(os.path.join(FEATURE_ROOT, 'domain/TodoModels.kt'))
    for label, token in DOMAIN_FEATURES:
        check(token in domain, 'todo domain provides ' + label)
    repository = read_source(os.path.join(FEATURE_ROOT, 'data/RoomTodoRepository.kt'))
    for label, token in (
        ('flow observation', 'override fun observeAll(): Flow<List<TodoItem>>'),
        ('room mapping', 'private fun toDomain(row: TodoTaskEntity)'),
        ('model mapping', 'private fun toEntity(item: TodoItem)'),
        ('local clock', 'override fun todayEpochDay(): Long = LocalDate.now().toEpochDay()'),
        ('completed cleanup', 'override suspend fun deleteCompleted(): Int = dao.deleteCompleted()'),
    ):
        check(token in repository, 'todo repository provides ' + label)
    view_model = read_source(os.path.join(FEATURE_ROOT, 'ui/TodoViewModel.kt'))
    for token in ('saved["todoFilter"]', 'saved["todoSort"]', 'saved["todoQuery"]'):
        check(token in view_model, 'todo ViewModel persists ' + token)
    check('if (id == null && allItems.size >= TodoPolicy.MAX_TASKS)' in view_model,
          'todo ViewModel enforces the 500-task limit')
    check('TodoPolicy.stats(allItems, today)' in view_model,
          'todo ViewModel computes statistics over all tasks')
    screen = read_source(os.path.join(FEATURE_ROOT, 'ui/TodoScreen.kt'))
    for label, token in UI_FEATURES:
        check(token in screen, 'todo UI provides ' + label)


def check_candidate_isolation(check):
    script = read_source(os.path.join(ROOT, 'scripts/check_todo_list_runtime.py'))
    harness = read_source(os.path.join(ROOT, 'scripts/check_admin_statistics_runtime.py'))
    check('run(sys.argv[1], extra_checks=verify, strict_isolation=True)' in script,
          'todo candidate opts into credential-stripped isolation')
    check("'redis-server', '--save', '', '--appendonly', 'no'" in harness
          and 'temporary_network = ' in harness, 'strict candidate uses disposable services and network')


def check_static(check):
    check_seed(check)
    check_android_resources(check)
    check_registration(check)
    check_database(check)
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
    print('Todo list offline static acceptance:', checks[0], '/', checks[0],
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

    check(sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version='77' AND success=1") == '1',
          'V77 applied')
    check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='%s'" % CODE) == '1',
          'V77 creates one todo row')
    check(CODE in records(), 'public LIFE catalog includes todo_list')
    check_tool_metadata(check, records()[CODE], 'public todo list', expected=defaults, public=True)
    detail = call(ADMIN_PATH, token=admin)
    listing = records(ADMIN_FILTER, admin)[CODE]
    check_tool_metadata(check, detail, 'admin todo detail', defaults)
    check_tool_metadata(check, listing, 'admin todo list', defaults)
    check(detail == listing, 'admin todo detail and list agree')
    categories = call('admin/tools/categories', token=admin)
    check(len([row for row in categories if row['code'] == 'LIFE' and row['enabled']]) == 1,
          'admin editor offers enabled LIFE category')

    edited = dict(defaults,
                  name=u'待办清单验收', description=u'后台编辑的待办清单说明。',
                  icon=CODE + '_admin_check', keywords=[u'验收', 'todo-list-admin'],
                  sortOrder=630, version=2, featured=True, requiresLogin=True,
                  configJson='{"acceptance":"todo_list"}')
    check_tool_metadata(check, call(ADMIN_PATH, edited, token=admin, method='PUT'),
                        'edited todo metadata', edited)
    for unused in range(2):
        call('tools/' + CODE + '/usage', method='POST')
    queries = [
        "SELECT * FROM tool_definition WHERE tool_code='%s'" % CODE,
        "SELECT * FROM flyway_schema_history WHERE version='77'",
        "SELECT * FROM tool_usage_daily WHERE tool_code='%s' ORDER BY usage_date" % CODE,
        "SELECT * FROM tool_usage_stat WHERE tool_code='%s'" % CODE,
    ]
    snapshot = [sql(query) for query in queries]
    source = read_source(SEED_PATH)
    sql(source)
    sql(source)
    check([sql(query) for query in queries] == snapshot,
          'V77 replay preserves operator edits, configuration, timestamps and usage')
    check_tool_metadata(check, call(ADMIN_PATH, token=admin),
                        'seed replay preserves edited todo', edited)
    check_tool_metadata(check, call(ADMIN_PATH, defaults, token=admin, method='PUT'),
                        'restored todo metadata', defaults)
    daily = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily WHERE tool_code='%s'" % CODE)
    total = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_stat WHERE tool_code='%s'" % CODE)
    check(daily == total == '2', 'todo increments daily and all-time usage')
    check(sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily WHERE tool_code='%s' "
              "AND usage_date=DATE(UTC_TIMESTAMP(3)+INTERVAL 8 HOUR)" % CODE) == '2',
          'todo daily opens use the Beijing date')
    popular = dict((row['code'], row) for row in call('tools/popular?limit=50')).get(CODE)
    check(popular is not None and popular['useCount'] == 2
          and all(popular[key] == CONTRACT[key] for key in ('name', 'description', 'icon')),
          'todo participates in popularity')
    for days in (7, 30, 90):
        data = call('admin/statistics?days=' + str(days), token=admin)
        check_statistics(check, data, days)
        for group, group_code in (('categories', 'LIFE'), ('modes', 'LOCAL')):
            groups = [row for row in data[group] if row['code'] == group_code]
            check(len(groups) == 1 and groups[0]['count'] >= 1 and groups[0]['opens'] >= 2,
                  str(days) + '-day ' + group + ' includes todo usage')
        ranks = [row for row in data['ranking'] if row['code'] == CODE]
        check(len(ranks) == 1 and ranks[0]['opens'] == ranks[0]['allTimeOpens'] == 2,
              'todo participates in ' + str(days) + '-day ranking')


def main():
    if len(sys.argv) == 2 and sys.argv[1] == '--static':
        offline_acceptance()
    elif len(sys.argv) == 2 and not sys.argv[1].startswith('-'):
        run(sys.argv[1], extra_checks=verify, strict_isolation=True)
    else:
        raise SystemExit('Usage: python -B scripts/check_todo_list_runtime.py --static | <candidate-image>')


if __name__ == '__main__':
    main()
