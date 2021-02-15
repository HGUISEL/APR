# -*- coding: utf-8 -*-
"""Untitled2.ipynb

Automatically generated by Colaboratory.

Original file is located at
    https://colab.research.google.com/drive/1qD9sPFQEUzl_rLvqltYgBgyz8ISbAh-h
"""

import os
import pandas as pd

def make_path(directory, file_name, is_make_temp_dir=False):
    if is_make_temp_dir is True:
        directory = mkdtemp()
    if len(directory) >= 2 and not os.path.exists(directory):
        os.makedirs(directory)    
    return os.path.join(directory, file_name)

DIR = './' # data가 저장된 폴더를 지정합니다 
for f in [make_path(DIR, file) for file in os.listdir(path=DIR) if '.csv' in file]:
    csv_data = pd.read_csv(f)
    suggested_changes = csv_data['suggested change info']
    original_changes = csv_data['orig change info']

all_changes = False
### for all changes
if(all_changes):
    train_csv = pd.read_csv('/home/goodtaeeun/APR_Projects/APR/pool/outputs/las/all_changes_train.csv')
    test_csv = pd.read_csv('/home/goodtaeeun/APR_Projects/APR/pool/outputs/las/all_changes_test.csv')
    suggested_changes = train_csv['change info']
    original_changes = test_csv['change info']

print(suggested_changes)
print(original_changes)

ast_lst = ['ANNOTATION_TYPE_DECLARATION', 'ANNOTATION_TYPE_MEMBER_DECLARATION', 'ANONYMOUS_CLASS_DECLARATION', 'ARRAY_ACCESS', 'ARRAY_CREATION', 'ARRAY_INITIALIZER', 'ARRAY_TYPE', 'ASSERT_STATEMENT', 'ASSIGNMENT', 'BLOCK', 'BLOCK_COMMENT', 'BOOLEAN_LITERAL', 'BREAK_STATEMENT', 'CAST_EXPRESSION', 'CATCH_CLAUSE', 'CHARACTER_LITERAL', 'CLASS_INSTANCE_CREATION', 'COMPILATION_UNIT', 'CONDITIONAL_EXPRESSION', 'CONSTRUCTOR_INVOCATION', 'CONTINUE_STATEMENT', 'CREATION_REFERENCE', 'DIMENSION', 'DO_STATEMENT', 'EMPTY_STATEMENT', 'ENHANCED_FOR_STATEMENT', 'ENUM_CONSTANT_DECLARATION', 'ENUM_DECLARATION', 'EXPORTS_DIRECTIVE', 'EXPRESSION_METHOD_REFERENCE', 'EXPRESSION_STATEMENT', 'FIELD_ACCESS', 'FIELD_DECLARATION', 'FOR_STATEMENT', 'IF_STATEMENT', 'IMPORT_DECLARATION', 'INFIX_EXPRESSION', 'INITIALIZER', 'INSTANCEOF_EXPRESSION', 'INTERSECTION_TYPE', 'JAVADOC', 'LABELED_STATEMENT', 'LAMBDA_EXPRESSION', 'LINE_COMMENT', 'MALFORMED', 'MARKER_ANNOTATION', 'MEMBER_REF', 'MEMBER_VALUE_PAIR', 'METHOD_DECLARATION', 'METHOD_INVOCATION', 'METHOD_REF', 'METHOD_REF_PARAMETER', 'MODIFIER', 'MODULE_DECLARATION', 'MODULE_MODIFIER', 'NAME_QUALIFIED_TYPE', 'NORMAL_ANNOTATION', 'NULL_LITERAL', 'NUMBER_LITERAL', 'OPENS_DIRECTIVE', 'ORIGINAL', 'PACKAGE_DECLARATION', 'PARAMETERIZED_TYPE', 'PARENTHESIZED_EXPRESSION', 'POSTFIX_EXPRESSION', 'PREFIX_EXPRESSION', 'PRIMITIVE_TYPE', 'PROTECT', 'PROVIDES_DIRECTIVE', 'QUALIFIED_NAME', 'QUALIFIED_TYPE', 'RECOVERED', 'REQUIRES_DIRECTIVE', 'RETURN_STATEMENT', 'SIMPLE_NAME', 'SIMPLE_TYPE', 'SINGLE_MEMBER_ANNOTATION', 'SINGLE_VARIABLE_DECLARATION', 'STRING_LITERAL', 'SUPER_CONSTRUCTOR_INVOCATION', 'SUPER_FIELD_ACCESS', 'SUPER_METHOD_INVOCATION', 'SUPER_METHOD_REFERENCE', 'SWITCH_CASE', 'SWITCH_STATEMENT', 'SYNCHRONIZED_STATEMENT', 'TAG_ELEMENT', 'TEXT_ELEMENT', 'THIS_EXPRESSION', 'THROW_STATEMENT', 'TRY_STATEMENT', 'TYPE_DECLARATION', 'TYPE_DECLARATION_STATEMENT', 'TYPE_LITERAL', 'TYPE_METHOD_REFERENCE', 'TYPE_PARAMETER', 'UNION_TYPE', 'USES_DIRECTIVE', 'VARIABLE_DECLARATION_EXPRESSION', 'VARIABLE_DECLARATION_FRAGMENT', 'VARIABLE_DECLARATION_STATEMENT', 'WHILE_STATEMENT', 'WILDCARD_TYPE']
operations = ['insert', 'update', 'delete', 'move']
def to_camel_case(snake_str):
    components = snake_str.split('_')
    return ''.join(x.title() for x in components[0:])

ast_types = []
for ast in ast_lst:
  ast_types.append(to_camel_case(ast))

print(ast_types)
print(operations)

import re
import math

pattern_ast_node = re.compile(r"(?=("+'|'.join(ast_types)+r"))")
pattern_operation = re.compile(r"(?=("+'|'.join(operations)+r"))")

original_change_result = []
suggested_change_result = []

for original_change_chunk, suggested_change_chunk in zip(original_changes, suggested_changes):
  if (suggested_change_chunk != suggested_change_chunk):
    suggested_change_result.append(['dummy'])
    original_change_result.append(['dummy'])
    continue
  original_changes = original_change_chunk.split("\n")
  suggested_changes = suggested_change_chunk.split("\n")
  original_change_info = []
  suggested_change_info = []
  
  # get changes of original change
  for one_change in original_changes:
    op = pattern_operation.findall(one_change)
    location = pattern_ast_node.findall(one_change)
    if (not op):
      continue
    parsed_single_change_info = op[0]
    for loc in location :
      parsed_single_change_info = parsed_single_change_info + ' ' + loc
    original_change_info.append(parsed_single_change_info)
  original_change_info.sort()
  original_change_result.append(original_change_info)


  # get changes of suggested chage
  for one_change in suggested_changes:
    op = pattern_operation.findall(one_change)
    location = pattern_ast_node.findall(one_change)
    if (not op):
       continue

    parsed_single_change_info = op[0]
    reverse_op_change_info = ""

    if (op[0] == 'insert'):
      reverse_op_change_info = 'delete'
    if (op[0] == 'delete'):
      reverse_op_change_info = 'insert'

    for loc in location :
      parsed_single_change_info = parsed_single_change_info + ' ' + loc
      reverse_op_change_info = reverse_op_change_info + ' ' + loc
    
    if ((parsed_single_change_info in original_change_result[-1]) and (parsed_single_change_info not in suggested_change_info)):
      suggested_change_info.append(parsed_single_change_info)
      if ((op[0] == 'insert' or op[0] == 'delete') and (reverse_op_change_info in original_change_result[-1]) and (reverse_op_change_info not in suggested_change_info)):
        suggested_change_info.append(reverse_op_change_info)
  suggested_change_info.sort()
  suggested_change_result.append(suggested_change_info)

print(original_change_result)
print(len(original_change_result))
print(suggested_change_result)
print(len(suggested_change_result))

df = pd.DataFrame(list(zip(original_change_result, suggested_change_result)), columns=['orig change info', 'suggested change info'])

csv_data['orig change info'] = df['orig change info']
csv_data['suggested change info'] = df['suggested change info']


original_change_count = []
suggested_change_count = []
isMatch = []

for result in original_change_result:
  original_change_count.append(len(result))
for result in suggested_change_result:
  suggested_change_count.append(len(result))

isMatch = [suggested_change_count[i] == original_change_count[i] for i in range(len(suggested_change_count)) ]
print(isMatch)

original_count = pd.DataFrame(original_change_count)
suggested_count = pd.DataFrame(suggested_change_count)
isMatch = pd.DataFrame(isMatch)
csv_data['original change count'] = original_count
csv_data['suggested change count'] = suggested_count
csv_data['Found All Ingredients?'] = isMatch

csv_data

csv_data.to_csv('./final_data.csv')

