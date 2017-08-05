# -*- coding: utf-8 -*-
"""
Created on Wed Jun  7 17:08:27 2017

@author: mininet
"""

from xml.etree import ElementTree
#读取配置文件
selection = ElementTree.parse("qosoverlldp.xml")
#载入数据到字典
qos_init = selection.getiterator("qos-init")[0]
pattern_dict = dict()
for pattern in qos_init:
    p_dict = dict()
    for p in pattern:
        p_dict.setdefault(p.tag,p.text)
    pattern_dict.setdefault(pattern.tag,p_dict)
#筛选出生效的数据
for p in pattern_dict.keys():
    if pattern_dict[p]["status"] in "false":
        pattern_dict.pop(p)
#如果没有一个数据生效则自动生成数据
if len(pattern_dict) is 0:
    pattern_dict.setdefault("even",{"bandwidth":"100Mbit","delay":"900us","jitter":"100us","loss":"0.001%"})
#格式化数据
pattern_key,pattern_value = pattern_dict.items()[0]
for k in pattern_value.keys():
    if "Gbit" in pattern_value[k]:
        v = pattern_value[k]
        pattern_value[k] = float(v[:len(v)-len("Gbit")])*1000
    elif "Mbit" in pattern_value[k]:
        v = pattern_value[k]
        pattern_value[k] = float(v[:len(v)-len("Mbit")])
    elif "Kbit" in pattern_value[k]:
        v = pattern_value[k]
        pattern_value[k] = float(v[:len(v)-len("Kbit")])/1000
    elif "bit" in pattern_value[k]:
        v = pattern_value[k]
        pattern_value[k] = float(v[:len(v)-len("bit")])/1000000
    elif "us" in pattern_value[k]:
        v = pattern_value[k]
        pattern_value[k] = float(v[:len(v)-len("us")])
    elif "ms" in pattern_value[k]:
        v = pattern_value[k]
        pattern_value[k] = float(v[:len(v)-len("ms")])*1000
    elif "s" in pattern_value[k]:
        v = pattern_value[k]
        pattern_value[k] = float(v[:len(v)-len("s")])*1000000
    elif "%" in pattern_value[k]:
        v = pattern_value[k]
        pattern_value[k] = float(v[:len(v)-len("%")])
    elif pattern_value[k].isdigit():
        v = pattern_value[k]
        pattern_value[k] = float(v)
print pattern_dict