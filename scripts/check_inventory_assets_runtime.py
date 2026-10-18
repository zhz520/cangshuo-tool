# -*- coding: utf-8 -*-
"""V81 inventory_assets static and isolated backend acceptance."""
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
CODE = 'inventory_assets'
CATALOG = 'tools?categoryCode=LIFE&pageSize=100'
ADMIN_PATH = 'admin/tools/' + CODE
ADMIN_FILTER = 'admin/tools?categoryCode=LIFE&keyword=inventory_assets&status=ENABLED&pageSize=100'
SEED_PATH = os.path.join(ROOT, 'server/src/main/resources/db/migration/V81__seed_inventory_assets_tool.sql')
JAVA_ROOT = os.path.join(ROOT, 'android/app/src/main/java')
RES_ROOT = os.path.join(ROOT, 'android/app/src/main/res')
FEATURE_ROOT = os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/feature/inventory')
RESOURCE_NAME = re.compile(r'^inventory_[A-Za-z0-9_]+$')
KEYWORDS_ZH = [u'物品', u'资产', u'库存', u'价格', u'位置', u'保质期', 'inventory', 'asset']
KEYWORDS_EN = ['inventory', 'asset', 'stock', 'price', 'location', 'expiry', u'物品', u'库存']
CONTRACT = {
    'code': CODE,
    'name': u'物品资产',
    'description': u'在本机记录物品、位置、数量、价格和到期日期，查看库存价值、低库存与即将到期提醒。',
    'categoryCode': 'LIFE',
    'mode': 'LOCAL',
    'icon': CODE,
    'sortOrder': 633,
    'requiresLogin': False,
    'featured': False,
    'status': 'ENABLED',
    'version': 1,
    'keywords': KEYWORDS_ZH,
}
ENGLISH = {
    'name': 'Inventory',
    'description': 'Track items, locations, quantities, prices and expiry dates locally, with value, low-stock and upcoming-expiry summaries.',
    'keywords': KEYWORDS_EN,
}
DOMAIN_FEATURES = (
    ('seven categories', 'enum class InventoryCategory'),
    ('filters', 'enum class InventoryFilter'),
    ('sorts', 'enum class InventorySort'),
    ('validation errors', 'enum class InventoryError'),
    ('expiry states', 'enum class ExpiryState'),
    ('item limit', 'const val MAX_ITEMS = 1_000'),
    ('quantity limit', 'const val MAX_QUANTITY = 999_999'),
    ('name budget', 'const val MAX_NAME_CODE_POINTS = 80'),
    ('notes budget', 'const val MAX_NOTES_CODE_POINTS = 500'),
    ('location budget', 'const val MAX_LOCATION_CODE_POINTS = 60'),
    ('unit budget', 'const val MAX_UNIT_CODE_POINTS = 12'),
    ('price ceiling', 'const val MAX_PRICE_CENTS = 99_999_999L'),
    ('price parser', 'fun parsePriceCents(text: String): Long?'),
    ('price formatting', 'fun formatPrice(cents: Long): String'),
    ('quantity formatting', 'fun formatQuantity(item: InventoryItem): String'),
    ('search filter sort', 'fun filterAndSort('),
    ('statistics', 'fun stats(items: List<InventoryItem>, todayEpochDay: Long): InventoryStats'),
    ('date validation', 'private fun validDate(epochDay: Long): Boolean'),
    ('use cases', 'class InventoryUseCases'),
)
REPOSITORY_FEATURES = (
    ('Room mapping', 'dao.observeAll().map'),
    ('atomic bounded insert', 'dao.insertIfBelowLimit('),
    ('missing update check', 'check(dao.update(toEntity(item)) > 0)'),
    ('atomic quantity adjustment', 'dao.adjustQuantity(id, delta, InventoryPolicy.MAX_QUANTITY, nowMillis()) > 0'),
    ('device local day', 'LocalDate.now().toEpochDay()'),
)
VIEWMODEL_FEATURES = (
    ('saved filter', 'saved.get<String>("inventoryFilter")'),
    ('saved sort', 'saved.get<String>("inventorySort")'),
    ('saved query', 'saved.get<String>("inventoryQuery")'),
    ('duplicate save guard', 'if (mutable.value.saving) return false'),
    ('early item limit guard', 'allItems.size >= InventoryPolicy.MAX_ITEMS'),
    ('busy quantity guard', 'if (item.id in mutable.value.busyItemIds) return'),
    ('busy delete guard', 'if (item.id in mutable.value.busyItemIds) return'),
    ('message consumption', 'fun consumeMessage()'),
)
UI_FEATURES = (
    ('overview stats', 'InventoryStatsCard('),
    ('search field', 'R.string.inventory_search'),
    ('filter chips', 'InventoryFilter.entries.forEach'),
    ('sort chips', 'InventorySort.entries.forEach'),
    ('loading state', 'ToolboxLoadingState('),
    ('empty state', 'R.string.inventory_empty_title'),
    ('asset cards', 'InventoryCard('),
    ('quick decrement', 'R.string.inventory_decrement_short'),
    ('quick increment', 'R.string.inventory_increment_short'),
    ('details dialog', 'InventoryDetailDialog('),
    ('editor dialog', 'InventoryEditorDialog('),
    ('date picker', 'DatePickerDialog('),
    ('purchase date', 'R.string.inventory_purchase_label'),
    ('expiry date', 'R.string.inventory_expiry_label'),
    ('category selection', 'InventoryCategory.entries.forEach'),
    ('bounded name input', 'InventoryPolicy.MAX_NAME_CODE_POINTS'),
    ('bounded notes input', 'InventoryPolicy.MAX_NOTES_CODE_POINTS'),
    ('delete confirmation', 'R.string.inventory_delete_title'),
    ('responsive wrapping', 'FlowRow('),
    ('status messages', 'SnackbarHost('),
)


def check_seed(check):
    source = read_source(SEED_PATH)
    check(source is not None, 'V81 catalog seed exists')
    migration_dir = os.path.dirname(SEED_PATH)
    files = sorted(name for name in os.listdir(migration_dir) if name.startswith('V81__'))
    check(files == [os.path.basename(SEED_PATH)], 'V81 has exactly one migration')
    body = '\n'.join(line for line in source.splitlines() if not line.lstrip().startswith('--'))
    columns = re.search(r'\A\s*INSERT INTO tool_definition\s*\(([^)]+)\)\s*VALUES\s*\(', body)
    check(columns is not None and [item.strip() for item in columns.group(1).split(',')] == [
        'tool_code', 'name', 'description', 'category_id', 'icon', 'keywords_json',
        'mode', 'requires_login', 'status', 'version', 'sort_order', 'is_featured',
    ], 'V81 only seeds existing tool_definition columns')
    strings = [item.replace("''", "'") for item in re.findall(r"'((?:''|[^'])*)'", body)]
    check(strings == [CODE, CONTRACT['name'], CONTRACT['description'], 'LIFE', CODE]
          + KEYWORDS_ZH + ['LOCAL', 'ENABLED'], 'V81 metadata and ordered values match')
    check(re.search(r"\(SELECT\s+id\s+FROM\s+tool_category\s+WHERE\s+code\s*=\s*'LIFE'\)", body)
          is not None, 'V81 category resolves LIFE by code')
    check(re.search(r"'LOCAL'\s*,\s*0\s*,\s*'ENABLED'\s*,\s*1\s*,\s*633\s*,\s*0\s*\)", body)
          is not None, 'V81 defaults and sort match')
    check(body.count('INSERT INTO tool_definition') == 1 and body.count(';') == 1,
          'V81 is one metadata insert')
    check(re.search(r'ON DUPLICATE KEY UPDATE\s+tool_code\s*=\s*tool_code\s*;\s*\Z', body) is not None,
          'V81 replay preserves operator edits')


def placeholders(value):
    return sorted(re.findall(r'%(?:\d+\$)?[a-zA-Z%]', value))


def check_resources(check):
    feature = ''
    for current, unused, names in os.walk(FEATURE_ROOT):
        for name in names:
            if name.endswith('.kt'):
                feature += '\n' + read_source(os.path.join(current, name))
    references = set(re.findall(r'R\.string\.(inventory_[A-Za-z0-9_]+)', feature))
    arrays = set(re.findall(r'R\.array\.(inventory_[A-Za-z0-9_]+)', feature))
    check({'inventory_name', 'inventory_description', 'inventory_search', 'inventory_save'}.issubset(references),
          'Inventory metadata and primary controls are referenced')
    check(arrays == {'inventory_keywords'}, 'Inventory keyword array is referenced')
    translations = {}
    for locale, expected, keywords in (('values', CONTRACT, KEYWORDS_ZH), ('values-en', ENGLISH, KEYWORDS_EN)):
        resources = ET.fromstring(read_source(os.path.join(RES_ROOT, locale, 'strings.xml')).encode('utf-8'))
        nodes = [node for node in resources.findall('string') if RESOURCE_NAME.match(node.get('name', ''))]
        names = [node.get('name') for node in nodes]
        translations[locale] = dict((node.get('name'), ''.join(node.itertext())) for node in nodes)
        check(len(names) == len(set(names)) and all(''.join(node.itertext()).strip() for node in nodes),
              'Android ' + locale + ' Inventory strings are unique and nonempty')
        check(set(translations[locale]) == references,
              'Android ' + locale + ' Inventory resource references are exact')
        check(translations[locale].get('inventory_name') == expected['name']
              and translations[locale].get('inventory_description') == expected['description'],
              'Android ' + locale + ' Inventory metadata matches')
        keyword_nodes = resources.findall("string-array[@name='inventory_keywords']")
        actual = [(node.text or '').strip() for node in keyword_nodes[0].findall('item')] if keyword_nodes else []
        check(len(keyword_nodes) == 1 and actual == keywords,
              'Android ' + locale + ' Inventory keywords match')
    check(set(translations['values']) == set(translations['values-en']), 'Inventory bilingual keys match')
    check(all(placeholders(value) == placeholders(translations['values-en'][name])
              for name, value in translations['values'].items()), 'Inventory bilingual placeholders match')


def check_registration(check):
    definition_path = os.path.join(FEATURE_ROOT, 'InventoryToolDefinition.kt')
    definition = read_source(definition_path)
    for label, token in (
        ('code', 'code = "inventory_assets"'),
        ('name', 'R.string.inventory_name'),
        ('description', 'R.string.inventory_description'),
        ('category', 'ToolCategory.LIFE'),
        ('mode', 'ToolMode.LOCAL'),
        ('icon', 'icon = "inventory_assets"'),
        ('keywords', 'R.array.inventory_keywords'),
        ('sort', 'sortOrder = 633'),
        ('featured', 'isFeatured = false'),
    ):
        check(token in definition, 'Inventory ToolDefinition ' + label)
    check('requiredPermissions: List<String> = emptyList()' in definition,
          'Inventory requests no Android permissions')
    owners = []
    for current, unused, names in os.walk(JAVA_ROOT):
        for name in names:
            if name.endswith('ToolDefinition.kt'):
                path = os.path.join(current, name)
                owners.extend([os.path.normpath(path)] *
                              len(re.findall(r'\bcode\s*=\s*"inventory_assets"', read_source(path))))
    check(owners == [os.path.normpath(definition_path)], 'Inventory has exactly one ToolDefinition owner')
    container = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/ToolboxAppContainer.kt'))
    registry = re.search(r'private fun bundledRegistry\b[\s\S]+?(?=private val registryStore)', container)
    check(registry is not None and len(re.findall(r'\bInventoryToolDefinition\s*\(', registry.group(0))) == 1,
          'Inventory is registered exactly once')
    check('RoomInventoryRepository(database.inventoryItemDao())' in container,
          'Inventory repository is bound to the Room DAO')
    icons = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/feature/home/ui/HomePresentation.kt'))
    check(re.search(r'"inventory_assets"\s*->\s*R\.drawable\.ic_inventory\b', icons) is not None,
          'Inventory has one icon mapping')
    icon = ET.parse(os.path.join(RES_ROOT, 'drawable/ic_inventory.xml')).getroot()
    android = '{http://schemas.android.com/apk/res/android}'
    check(icon.tag == 'vector' and icon.get(android + 'width') == '24dp'
          and icon.get(android + 'height') == '24dp'
          and any(node.get(android + 'pathData', '').strip() for node in icon.iter('path')),
          'Inventory icon is a nonempty 24dp vector')


def check_database(check):
    database = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/core/database/ToolboxDatabase.kt'))
    check('InventoryItemEntity::class' in database and 'version = 12' in database
          and 'abstract fun inventoryItemDao(): InventoryItemDao' in database,
          'Room exports inventory at schema version 12')
    migration = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/core/database/ToolboxDatabaseMigrations.kt'))
    check('val MIGRATION_11_12 = object : Migration(11, 12)' in migration,
          'Room declares 11 to 12 migration')
    for token in ('CREATE TABLE IF NOT EXISTS inventory_item', 'name TEXT NOT NULL', 'notes TEXT NOT NULL',
                  'category INTEGER NOT NULL', 'location TEXT NOT NULL', 'quantity INTEGER NOT NULL',
                  'unit TEXT NOT NULL', 'unit_price_cents INTEGER', 'low_stock_threshold INTEGER NOT NULL',
                  'purchase_epoch_day INTEGER', 'expiry_epoch_day INTEGER', 'created_at INTEGER NOT NULL',
                  'updated_at INTEGER NOT NULL', 'index_inventory_item_category',
                  'index_inventory_item_expiry_epoch_day', 'index_inventory_item_updated_at'):
        check(token in migration, 'Room migration contains ' + token)
    schema_path = os.path.join(ROOT, 'android/app/schemas/com.cangshuo.toolbox.core.database.ToolboxDatabase/12.json')
    schema = json.loads(read_source(schema_path))
    check(schema['database']['version'] == 12, 'Room schema 12 exported')
    entities = dict((row['tableName'], row) for row in schema['database']['entities'])
    check('inventory_item' in entities, 'Room schema contains inventory_item')
    event = entities['inventory_item']
    check(event['primaryKey'] == {'autoGenerate': True, 'columnNames': ['id']},
          'Inventory uses a generated id')
    check(set(row['columnName'] for row in event['fields']) == {
        'id', 'name', 'notes', 'category', 'location', 'quantity', 'unit', 'unit_price_cents',
        'low_stock_threshold', 'purchase_epoch_day', 'expiry_epoch_day', 'created_at', 'updated_at',
    }, 'Inventory columns match migration')
    check(set(tuple(index['columnNames']) for index in event.get('indices', [])) == {
        ('category',), ('expiry_epoch_day',), ('updated_at',),
    }, 'Inventory exports all three indexes')
    dao = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/core/database/InventoryItemDao.kt'))
    for label, token in (
        ('transactional count', '@Transaction'),
        ('bounded count query', 'SELECT COUNT(*) FROM inventory_item'),
        ('transactional bounded insert', 'suspend fun insertIfBelowLimit(item: InventoryItemEntity, limit: Int): Long'),
        ('limit sentinel', 'if (countItems() >= limit) return -1L'),
        ('checked update', 'suspend fun update(item: InventoryItemEntity): Int'),
        ('clamped quantity', 'quantity = MAX(0, MIN(:maxQuantity, quantity + :delta))'),
        ('updated timestamp', 'updated_at = :updatedAt'),
    ):
        check(token in dao, 'Inventory DAO provides ' + label)


def check_implementation(check):
    domain = read_source(os.path.join(FEATURE_ROOT, 'domain/InventoryModels.kt'))
    for label, token in DOMAIN_FEATURES:
        check(token in domain, 'Inventory domain provides ' + label)
    check('expiry <= todayEpochDay + 30L' in domain and 'quantity <= lowStockThreshold' in domain,
          'Inventory low-stock and 30-day boundaries are explicit')
    repository = read_source(os.path.join(FEATURE_ROOT, 'data/RoomInventoryRepository.kt'))
    for label, token in REPOSITORY_FEATURES:
        check(token in repository, 'Inventory repository provides ' + label)
    view_model = read_source(os.path.join(FEATURE_ROOT, 'ui/InventoryViewModel.kt'))
    for label, token in VIEWMODEL_FEATURES:
        check(token in view_model, 'Inventory ViewModel provides ' + label)
    screen = read_source(os.path.join(FEATURE_ROOT, 'ui/InventoryScreen.kt'))
    for label, token in UI_FEATURES:
        check(token in screen, 'Inventory UI provides ' + label)
    for token in ('safeDrawingPadding()', 'imePadding()', 'verticalScroll(rememberScrollState())',
                  'rememberLazyListState()', 'maxLines = 2', 'heightIn(max = 820.dp)',
                  'widthIn(max = ToolboxDimens.formMaxWidth).fillMaxWidth()',
                  'inventory_action_for_item', 'messageRevision'):
        check(token in screen, 'Inventory UI adaptation contains ' + token)
    route = read_source(os.path.join(FEATURE_ROOT, 'ui/InventoryRoute.kt'))
    check('repeatOnLifecycle(Lifecycle.State.RESUMED)' in route and 'delay(60_000L)' in route,
          'Inventory refreshes expiry state while resumed')
    tests = []
    for current, unused, names in os.walk(os.path.join(ROOT, 'android/app/src/test/java/com/cangshuo/toolbox/feature/inventory')):
        tests.extend(name for name in names if name.endswith('Test.kt'))
    check(sorted(tests) == ['InventoryPolicyTest.kt', 'InventoryViewModelTest.kt'],
          'Inventory has focused policy and ViewModel suites')
    docs = read_source(os.path.join(ROOT, 'docs/INVENTORY_ASSETS.md'))
    check(docs is not None and 'google-developer-training/android-basics-kotlin-inventory-app' in docs
          and 'Apache-2.0' in docs and 'ce377892b66380a006ec4b4d4fa2f4baa14c16d0' in docs,
          'Inventory documents the fixed open-source review')


def check_candidate_isolation(check):
    script = read_source(os.path.join(ROOT, 'scripts/check_inventory_assets_runtime.py'))
    check('run(sys.argv[1], extra_checks=verify, strict_isolation=True)' in script,
          'Inventory candidate opts into credential-stripped isolation')
    container = read_source(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/ToolboxAppContainer.kt'))
    check('MIGRATION_11_12' in container, 'Inventory migration is wired into the app database')


def check_static(check):
    check_seed(check)
    check_resources(check)
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
    print('Inventory assets offline static acceptance:', checks[0], '/', checks[0],
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

    check(sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version='81' AND success=1") == '1', 'V81 applied')
    check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='%s'" % CODE) == '1',
          'V81 creates one inventory row')
    check(CODE in records(), 'public LIFE catalog includes inventory_assets')
    check_tool_metadata(check, records()[CODE], 'public inventory assets', expected=defaults, public=True)
    detail = call(ADMIN_PATH, token=admin)
    listing = records(ADMIN_FILTER, admin)[CODE]
    check_tool_metadata(check, detail, 'admin inventory detail', defaults)
    check_tool_metadata(check, listing, 'admin inventory list', defaults)
    check(detail == listing, 'admin inventory detail and list agree')
    categories = call('admin/tools/categories', token=admin)
    check(len([row for row in categories if row['code'] == 'LIFE' and row['enabled']]) == 1,
          'admin editor offers enabled LIFE category')

    edited = dict(defaults, name=u'物品资产验收', description=u'后台编辑的物品资产说明。',
                  icon=CODE + '_admin_check', keywords=[u'验收', 'inventory-admin'],
                  sortOrder=634, version=2, featured=True, requiresLogin=True,
                  configJson='{"acceptance":"inventory_assets"}')
    check_tool_metadata(check, call(ADMIN_PATH, edited, token=admin, method='PUT'),
                        'edited inventory metadata', edited)
    for unused in range(2):
        call('tools/' + CODE + '/usage', method='POST')
    queries = ["SELECT * FROM tool_definition WHERE tool_code='%s'" % CODE,
               "SELECT * FROM flyway_schema_history WHERE version='81'",
               "SELECT * FROM tool_usage_daily WHERE tool_code='%s' ORDER BY usage_date" % CODE,
               "SELECT * FROM tool_usage_stat WHERE tool_code='%s'" % CODE]
    snapshot = [sql(query) for query in queries]
    source = read_source(SEED_PATH)
    sql(source)
    sql(source)
    check([sql(query) for query in queries] == snapshot, 'V81 replay preserves operator edits and usage')
    check_tool_metadata(check, call(ADMIN_PATH, token=admin), 'seed replay preserves edited inventory', edited)
    check_tool_metadata(check, call(ADMIN_PATH, defaults, token=admin, method='PUT'),
                        'restored inventory metadata', defaults)
    daily = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily WHERE tool_code='%s'" % CODE)
    total = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_stat WHERE tool_code='%s'" % CODE)
    check(daily == total == '2', 'Inventory increments daily and all-time usage')
    popular = dict((row['code'], row) for row in call('tools/popular?limit=50')).get(CODE)
    check(popular is not None and popular['useCount'] == 2
          and all(popular[key] == CONTRACT[key] for key in ('name', 'description', 'icon')),
          'Inventory participates in popularity')
    for days in (7, 30, 90):
        data = call('admin/statistics?days=' + str(days), token=admin)
        check_statistics(check, data, days)
        for group, group_code in (('categories', 'LIFE'), ('modes', 'LOCAL')):
            rows = [row for row in data[group] if row['code'] == group_code]
            check(len(rows) == 1 and rows[0]['count'] >= 1 and rows[0]['opens'] >= 2,
                  str(days) + '-day ' + group + ' includes inventory usage')
        ranks = [row for row in data['ranking'] if row['code'] == CODE]
        check(len(ranks) == 1 and ranks[0]['opens'] == ranks[0]['allTimeOpens'] == 2,
              'Inventory participates in ' + str(days) + '-day ranking')


def main():
    if len(sys.argv) == 2 and sys.argv[1] == '--static':
        offline_acceptance()
    elif len(sys.argv) == 2 and not sys.argv[1].startswith('-'):
        run(sys.argv[1], extra_checks=verify, strict_isolation=True)
    else:
        raise SystemExit('Usage: python -B scripts/check_inventory_assets_runtime.py --static | <candidate-image>')


if __name__ == '__main__':
    main()
