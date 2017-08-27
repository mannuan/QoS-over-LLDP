/**
 * Copyright (c) 2016 - 2017 WangLing Lab, Inc.
 *
 *  You may obtain a copy of the License at:
 *
 *       http://www.swunix.com
 *
 *       vlog默认的输出文件是/var/log/openvswitch/ovs-vswitchd.log
 *       在floodlight上面设置qostlv的时候要注意了,不要把qostlv的位置放在timestamp的后面，否则就会影响floodlight的功能
 *       tc查看命令：带宽 tc class show dev s1-eth1, 延迟、抖动、丢包率 tc qdisc show dev s1-eth1
 *       使用tc命令把qos设置为0，是无法设置的，所以在判断0的时候无需考虑太多
 *       带宽和丢包率这两个qos值是独立的，不受其他qos信息的影响,但是延迟和抖动是相互影响的，唯有设置了延迟才可以设置抖动，
 *       因此，在openvswitch设置jitter的时候应该先判断有无delay,否则不需要判断
 *       注意：linux 的tc 支持带宽设置的范围为:8bit~1Gbit;(而且只能8bit的设置)单位有:bit,Kbit,Mbit,Gbit
 *                   delay延迟的范围(0,274877906]us,mininet设置的单位默认是us,也就是延迟最大是247.9s，然后tc设置时间的时候只保留到小数点后一位
 *                   单位有:us,ms,s
 *                   抖动(jitter)的范围(0,274877906]        单位:us 注意:jitter是依赖于delay的,也就是说没有delay就没有jitter,有delay但是可以没有jitter
 *                   丢包率(delay)的范围[0,100]
 *       在floodlight中加入qostlv时千万不可以改变chassisid和portidtlv的位置,其他随便,否则qos over lldp项目无法实现,但是不会影响交换机的正常工作
 *       在mininet中设置0.000008Mbit(8bit)的时候，在终端可能显示0.00Mbit,但是确实设置进去了,如下:
 *       class htb 5:1 root leaf 10: prio 0 rate 8bit ceil 8bit burst 1b cburst 225b
 *       把实现qostlv的功能加在了net.floodlightcontroller.linkdiscovery.internal.LinkDiscoveryManager.generateLLDPMessage方法里面
 *       只需要一行代码:lldp.getOptionalTLVList().add(qosTLV);(放这行代码要注意:不要把qostlv的位置放在timestamp的后面)
 *       另外由于要改lldp发送的时间间隔,所以要改net.floodlightcontroller.linkdiscovery.internal.LinkDiscoveryManager中的LLDP_TO_ALL_INTERVAL参数,
 *       把protected final int LLDP_TO_ALL_INTERVAL = 15;把protected final修饰符改为public static修饰符
 *       还有要/wm/qosoverlldp/proactive/json立刻生效就要修改lldpClock的属性把protected final 改为public static
 *       把protected final long lldpClock = 0;把protected final修饰符改为public static修饰符
 *       把对LLDP进行QoS信息的填写的功能加在packet_out解码的函数里面，具体位置是ofp-util.c的文件的ofputil_decode_packet_out函数里面，大概在文件的第4190行
 *		 填写的位置如下所示：
 *
 *		 struct ofpbuf b = ofpbuf_const_initializer(oh, ntohs(oh->length));
 *
 *		 add_qos_to_lldp(&b);
 *
 *		 也就是放在ofpbuf下面，因为我们的主程序要调用它
 *
 *		 在进行floodlight编程的时候，一定要注意：千万不要乱导包，导入的外部包类名如果与原来的包冲突，就会影响整个floodlight的正常运行
 *		 为了使QoSoverLLDP模块生效，要在如下两个配置文件里面添加net.floodlightcontroller.qosoverlldp.internal.QoSoverLLDPManager
 *		 qol-fl/floodlight-v5.0/src/main/resources/META-INF/services/net.floodlightcontroller.core.module.IFloodlightModule
 *		 qol-fl/floodlight-v5.0/src/main/resources/floodlightdefault.properties
 *		 以上两个文件的添加方式是不同的，第一个配置只需要加在文件末尾即可
 *		 但是第二个配置文件，必须考虑清楚，应该如下：
 *		 net.floodlightcontroller.statistics.StatisticsCollector,\
 *       net.floodlightcontroller.qosoverlldp.internal.QoSoverLLDPManager
 *       也就是说上面要加的那句话应该放在net.floodlightcontroller这类模块的最后，并与上一个模块用逗号隔开
 */

#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <stdarg.h>
#include <sys/time.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <dirent.h>
#include <pthread.h>
#include "openvswitch/dynamic-string.h"
#include "openvswitch/vlog.h"
#include "openvswitch/ofpbuf.h"

    // ***************
    // Error Macro definition related
    // ***************

#define ERROR_ISNOTLLDP "err: The packet does't contain lldp" /**不是lldp的packet_in的数据包*/
#define ERROR_NOTFOUND_CHASSISID "err: Can't find chassisid"/**出现意外错误!无法在lldp里面找到chassisid,请检查代码是否出错,还是系统运行异常*/
#define ERROR_NOTFOUND_PORTID "err: Can't find portid"/**出现意外错误!无法在lldp里面找到portid,请检查代码是否出错,还是系统运行异常*/
#define ERROR_WITHOUT_QOSTLV "err: There is not qostlv"/**这个lldp的packet_in的数据包没有qos TLV单元*/
#define ERROR_CANTCREATE_FILENO "err: Can't create fileno"/**执行cmd命令时,无法创建文件句柄*/

    // ***************
    // LLDP Macro definition related
    // ***************

#define LINK_LAYER_DISCOVERY_PROTOCOL_CHASSISID_FLAG "88cc020704"
#define LINK_LAYER_DISCOVERY_PROTOCOL_PORTID_FLAG "040302"
#define TLV_TYPE 127
#define TLV_SUBTYPE "abcdef66"//tlv的subtype,包含在tlv的内容里面,计算长度的时候也要算进去
#define BANDWIDTH_SIZE 8//8个字节
#define DELAY_SIZE 8//8个字节
#define JITTER_SIZE 8//8个字节
#define LOSS_SIZE 8//8个字节

    // ***************
    // QoS Macro definition related
    // ***************

#define BUFFERSIZE 102400//这个千万不要改，改了就错了，要足够大，4096太小
char buf[BUFFERSIZE];/**注意:它必须是全局变量,注意buf不仅存放返回值，而且存放执行的字符命令，因此buf一定要放得下命令*/
//下面的这些宏定义，可以修改,但是不可以删除
#define INTERVAL "1"//获取qos信息的时间间隔
#define BANDWIFTH_FILE "remainbw"//记录带宽的文件
#define DELAY_FILE "delay"//记录时延的文件
#define JITTER_FILE "jitter"//记录抖动的文件
#define LOSS_FILE "loss"//记录丢包率的文件
#define QOSOVERLLDP_SH_NAME "dynamic-qos.sh"//实时计算qos信息的linux shell脚本
#define QOSOVERLLDP_FOLDER_NAME "/tmp/dynamic-qos_qosoverlldp/"//用于存放实时qos信息的绝对路径，前几级目录必须存在，后面一级目录可以不存在，最后面的‘/’绝对不可以掉
//下面的这个宏定义是linux脚本的字符串，只要修改这个宏定义就可以实现你想要的功能
#define QOSOVERLLDP_SH_CONTENT "DEV=$1\nif [ $# -eq 1 ];then INTERVAL=1;remainbwfile=\"remainbw\";delayfile=\"delay\";jitterfile=\"jitter\";lossfile=\"loss\";qosoverlldpshfile=\"dynamic-qos.sh\";path=\"/tmp/dynamic-qos_qosoverlldp/\";else INTERVAL=$2;remainbwfile=$3;delayfile=$4;jitterfile=$5;lossfile=$6;qosoverlldpshfile=$7;path=$8;fi\nClear(){\nrm -rf $path$DEV$remainbwfile $path$DEV$delayfile $path$DEV$jitterfile $path$DEV$lossfile $path$qosoverlldpshfile\nif [ \"`ls -A $path`\" = \"\" ];then rm -rf $path;fi\nbreak\n}\nwhile true;do\ncd /sys/devices/virtual/net/$DEV/statistics/;if [ $? -ne 0 ];then Clear;fi\nrtbit1=`cat rx_bytes tx_bytes | xargs | awk '{print ($1+$2)*8}'`\nsentpkt_dropped1=`tc -s class show dev $DEV | grep 'pkt' | tr -cd ' [0-9]' | awk '{print $2,$3}'`\nsleep $INTERVAL\nrtbit2=`cat rx_bytes tx_bytes | xargs | awk '{print ($1+$2)*8}'`\nsentpkt_dropped2=`tc -s class show dev $DEV | grep 'pkt' | tr -cd ' [0-9]' | awk '{print $2,$3}'`\nsetbw=`tc class show dev $DEV | grep 'rate' | sed 's/^.*rate //g' | sed 's/bit.*$//g' | sed 's/K/000/g' | sed 's/M/000000/g' | sed 's/G/000000000/g'`\noccupybw=$(awk 'BEGIN{print int(('$rtbit2'-'$rtbit1')/('$INTERVAL'+0.1))}')\nremainbw=$(awk 'BEGIN{if('$setbw'<'$occupybw') print 100; else print '$setbw'-'$occupybw'}');if [ $? -ne 0 ];then Clear;fi\npkt=`echo $sentpkt_dropped1 $sentpkt_dropped2 | awk '{print $3-$1}'`;if [ $? -ne 0 ];then Clear;fi\nsetloss=`tc -s qdisc show dev $DEV | grep 'loss' | tr -cd ' .[0-9]' | awk '{print $6}'`\nloss=`echo $sentpkt_dropped1 $sentpkt_dropped2 | awk '{if(($3-$1)<=0) print 0; else print ($4-$2)/($4+$3-$2-$1)*100}'`;if [ $? -ne 0 ];then Clear;fi\nloss=$(awk 'BEGIN{if('$loss'>'$setloss') print '$setloss'; else print '$loss'}')\nsetdelay=`tc -s qdisc show dev $DEV | grep 'delay' | sed 's/^.*delay //g' | sed 's/ .*$//g' | sed 's/us/ us/g' | sed 's/ms/ ms/g' | sed 's/s/ s/g' | sed 's/u/1/g' |sed 's/m/1000/g' | sed 's/s/1000000/g' | awk '{print $1*$2}'`\nsetjitter=`tc -s qdisc show dev $DEV | grep 'delay' | sed 's/^.*  //g' | sed 's/ .*$//g' | sed 's/us/ us/g' | sed 's/ms/ ms/g' | sed 's/s/ s/g' | sed 's/u/1/g' |sed 's/m/1000/g' | sed 's/s/1000000/g' | awk '{print $1*$2}'`\ndelay=$(awk 'BEGIN{if('$pkt'>0) print ('$INTERVAL'/'$pkt' )*1000000; else print 0}')\ndelay=$(awk 'BEGIN{if('$delay'>'$setdelay') print '$setdelay'; else print '$delay'}');if [ $? -ne 0 ];then Clear;fi\nif [ -f \"$path$DEV$jitterfile\" ];then predelay=`cat $path$DEV$delayfile`;else predelay=0;fi\njitter=$(awk 'BEGIN{if('$delay' > '$predelay') print '$delay' - '$predelay'; else print '$predelay' - '$delay'}')\ncd $path;if [ $? -ne 0 ];then Clear;fi\necho $remainbw > $DEV$remainbwfile;echo $loss > $DEV$lossfile;echo $delay > $DEV$delayfile;echo $jitter > $DEV$jitterfile\ndone"

	// ***************
	// Function Macro definition related
	// ***************

void add_qos_to_lldp(struct ofpbuf *packet);/**把qos信息加入到lldp数据包中*/
char* bytes_to_string(const void *buf_, size_t maxbytes);/**把数据转换成16进制字符串*/
char* hexstr_to_longstr(char* hstr, int len);/**把16进制的字符串转换成长整型*/
char *xm_vsprintf_ex(int len, char *fmt, ... );/**字符串拼接函数*/
int send_cmd(char* cmd);/**执行cmd命令*/
void Reverse(unsigned char* p,int size);/**字符串翻转*/
void read_set(char* file,unsigned char* dst,int dst_size,char type);/**读取qos信息，并把它填入报文*/
