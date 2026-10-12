"""Verify V44-V47 catalog, administrator controls and statistics in the isolated candidate.

Runs with Python 2/3 stdlib on the deployment host. Reuses isolated MySQL/API
setup and cleanup; never mutates the production schema or prints credentials.
Usage: python scripts/check_calc_catalog_runtime.py <candidate-image>
"""
from __future__ import print_function
import os
import sys
import xml.etree.ElementTree as ET
from check_admin_statistics_runtime import run


def verify(check, request, sql, admin):
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    resources = ET.parse(os.path.join(root, 'android/app/src/main/res/values/strings.xml')).getroot()
    strings = dict((node.get('name'), node.text) for node in resources.findall('string'))
    keywords = dict((node.get('name'), [item.text for item in node]) for node in resources.findall('string-array'))
    specs = [('bmi', 'bmi', 30, 'CALC'), ('compound_interest', 'compound', 35, 'CALC'),
             ('mortgage', 'mortgage', 40, 'CALC'), ('fuel', 'fuel', 45, 'CALC'),
             ('date_calc', 'date_calc', 50, 'CALC'), ('color_converter', 'color', 55, 'CALC'),
             ('password_gen', 'password', 60, 'TEXT'), ('text_counter', 'text_count', 65, 'TEXT'),
             ('url_extract', 'url_extract', 70, 'TEXT'), ('kinship', 'kinship', 75, 'CALC'),
             ('number_chinese', 'number_chinese', 80, 'TEXT'), ('char_convert', 'char_convert', 85, 'TEXT'),
             ('text_similarity', 'similarity', 90, 'TEXT')]
    seeds = []
    for name in ('V44__seed_calc_tools.sql', 'V45__seed_date_tool.sql', 'V46__seed_color_tool.sql',
                 'V47__seed_text_tools.sql', 'V48__seed_kinship_tool.sql', 'V49__seed_text_tools_batch.sql'):
        with open(os.path.join(root, 'server/src/main/resources/db/migration', name), 'rb') as stream:
            seeds.append(stream.read().decode('utf-8'))
    check(sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('44','45','46','47','48','49') AND success=1") == '6',
          'V44-V49 applied')
    status, body, unused = request('tools?pageSize=100')
    check(status == 200, 'public tool catalog available')
    catalog = dict((row['code'], row) for row in body['data']['records'])
    for code, prefix, order, category in specs:
        check(code in catalog, 'public catalog includes ' + code)
        row = catalog[code]
        check(row['mode'] == 'LOCAL' and row['categoryCode'] == category and row['sortOrder'] == order,
              'catalog mode/category/order ' + code)
        check(row['name'] == strings[prefix + '_name'] and row['description'] == strings[prefix + '_description']
              and row['keywords'] == keywords[prefix + '_keywords'] and row['icon'] == code,
              'Android and API metadata agree ' + code)
        check(request('admin/tools/' + code)[0] == 401, 'anonymous tool administration denied ' + code)
        status, detail, unused = request('admin/tools/' + code, token=admin)
        check(status == 200 and detail['data']['code'] == code and detail['data']['status'] == 'ENABLED',
              'administrator tool detail ' + code)
        check(request('tools/' + code + '/usage', method='POST')[0] == 200, 'usage accepted ' + code)
    stats = request('admin/statistics?days=7', token=admin)[1]['data']
    ranking = dict((row['code'], row) for row in stats['ranking'])
    for code, unused_prefix, unused_order, unused_category in specs:
        check(code in ranking and ranking[code]['opens'] == 1 and ranking[code]['allTimeOpens'] == 1,
              'daily and cumulative ranking ' + code)
    check(stats['summary']['allTimeOpens'] == 25 and stats['summary']['windowOpens'] == 25,
          'new tools participate in aggregate statistics')

    check(request('admin/tools/bmi/status', {'status': 'MAINTENANCE'}, token=admin, method='PATCH')[0] == 200,
          'administrator can maintain new tool')
    check('bmi' not in [row['code'] for row in request('tools?pageSize=100')[1]['data']['records']],
          'maintenance removes public catalog entry')
    check(request('admin/tools/fuel', token=admin, method='DELETE')[0] == 200, 'administrator can soft-delete new tool')
    for seed in seeds:
        sql(seed)
    check(sql("SELECT status FROM tool_definition WHERE tool_code='bmi'") == 'MAINTENANCE',
          'replayed seed preserves operator status')
    check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='fuel' AND deleted_at IS NOT NULL") == '1',
          'replayed seed preserves deletion')
    check(request('admin/tools/fuel', token=admin)[0] == 404, 'deleted tool stays deleted')
    check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code IN ('bmi','compound_interest','mortgage','fuel','date_calc','color_converter','password_gen','text_counter','url_extract','kinship','number_chinese','char_convert','text_similarity')") == '13',
          'replayed seed introduces no duplicates')
    check(request('admin/tools/bmi/status', {'status': 'ENABLED'}, token=admin, method='PATCH')[0] == 200,
          'administrator can re-enable new tool')


if __name__ == '__main__':
    run(sys.argv[1], extra_checks=verify)
