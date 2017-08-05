//Flot Line Chart
$(document).ready(function() {
    console.log("document ready");
    // var offset = 0;
    function plot() {
        var switchid = document.getElementById("switchid_select").value;
        var portid = document.getElementById("portid_select").value;
        var device = "s"+switchid+"-eth"+portid;
        var bandwidtharr = [],
            delayarr = [],
            jitterarr = [],
            lossarr = [],
            latencyarr = [];
        $.ajax({
            url:"/wm/qosoverlldp/webgui/statistics/percentagelist/"+device+"/json",
            datatype:"json",
            async:false,
            success:function(data){
                bandwidth=data["bandwidth"];
                delay=data["delay"];
                jitter=data["jitter"];
                loss=data["loss"];
                latency=data["latency"];
                for(var i=0;i<bandwidth.length;i++){
                    bandwidtharr.push([i,bandwidth[i]+10]);
                    delayarr.push([i,delay[i]+20]);
                    jitterarr.push([i,jitter[i]+30]);
                    lossarr.push([i,loss[i]+40]);
                    latencyarr.push([i,latency[i]+50]);
                }
            }
        });

        var options = {
            series: {
                lines: {
                    show: true
                },
                points: {
                    show: true
                }
            },
            grid: {
                hoverable: true //IMPORTANT! this is needed for tooltip to work
            },
            yaxis: {
                min: 0,
                max: 150
            },
            tooltip: true,
            tooltipOpts: {
                content: "'%s'曲线，第%x次的百分比：%y.4%",
                shifts: {
                    x: -60,
                    y: 125
                }
            }
        };

        var plotObj = $.plot($("#flot-line-chart"), [{
                data: bandwidtharr,
                label: "bandwidth"
            }, {
                data: delayarr,
                label: "delay"
            },{
                data: jitterarr,
                label: "jitter"
            },{
                data: lossarr,
                label: "loss"
            },{
                data: latencyarr,
                label: "latency"
            }],
            options);
    }
    plot();
    var switchid = document.getElementById("switchid_select").value;
    var portid = document.getElementById("portid_select").value;
    var device = "s"+switchid+"-eth"+portid;
    document.getElementById("historyqosvariety_line").innerHTML="端口"+device+"的历史服务质量变化——曲线图";
    var flot_line_chart = document.getElementById("flot-line-chart");
    flot_line_chart.onmouseover=function(){
        plot();
        var switchid = document.getElementById("switchid_select").value;
        var portid = document.getElementById("portid_select").value;
        var device = "s"+switchid+"-eth"+portid;
        document.getElementById("historyqosvariety_line").innerHTML="端口"+device+"的历史服务质量变化——曲线图";
    }
    $("#switchid_select").change(function(){
        plot();
        var switchid = document.getElementById("switchid_select").value;
        var portid = document.getElementById("portid_select").value;
        var device = "s"+switchid+"-eth"+portid;
        document.getElementById("historyqosvariety_line").innerHTML="端口"+device+"的历史服务质量变化——曲线图";
    });
    $("#portid_select").change(function(){
        plot();
        var switchid = document.getElementById("switchid_select").value;
        var portid = document.getElementById("portid_select").value;
        var device = "s"+switchid+"-eth"+portid;
        document.getElementById("historyqosvariety_line").innerHTML="端口"+device+"的历史服务质量变化——曲线图";
    });
    window.setInterval(plot,1000);
});

//Flot Pie Chart
$(function() {

    function plot_pie(){
        var switchid = document.getElementById("switchid_select").value;
        var portid = document.getElementById("portid_select").value;
        var device = "s"+switchid+"-eth"+portid;
        var bandwidth,
            delay,
            jitter,
            loss,
            latency;
        $.ajax({
            url:"/wm/qosoverlldp/webgui/statistics/varietyproportion/"+device+"/json",
            datatype:"json",
            async:false,
            success:function(data){
                bandwidth=data["bandwidth"];
                delay=data["delay"];
                jitter=data["jitter"];
                loss=data["loss"];
                latency=data["latency"];
                console.log(bandwidth);
            }
        });



        var data = [{
            label: "bandwidth",
            data: bandwidth
        }, {
            label: "delay",
            data: delay
        }, {
            label: "jitter",
            data:jitter
        }, {
            label: "loss",
            data: loss
        },{
            label: "latency",
            data: latency
        }];

        var plotObj = $.plot($("#flot-pie-chart"), data, {
            series: {
                pie: {
                    show: true
                }
            },
            grid: {
                hoverable: true
            },
            tooltip: true,
            tooltipOpts: {
                content: "%s的比重：%p.0%", // show percentages, rounding to 2 decimal places
                shifts: {
                    x: 20,
                    y: 0
                },
                defaultTheme: false
            }
        });
    }
    plot_pie();
    var switchid = document.getElementById("switchid_select").value;
    var portid = document.getElementById("portid_select").value;
    var device = "s"+switchid+"-eth"+portid;
    document.getElementById("historyqosvariety_pie").innerHTML="端口"+device+"的历史服务质量变化剧烈程度对比——饼状图";
    var flot_pie_chart = document.getElementById("flot-pie-chart");
    flot_pie_chart.onmouseover=function(){
        plot_pie();
        var switchid = document.getElementById("switchid_select").value;
        var portid = document.getElementById("portid_select").value;
        var device = "s"+switchid+"-eth"+portid;
        document.getElementById("historyqosvariety_pie").innerHTML="端口"+device+"的历史服务质量变化剧烈程度对比——饼状图";
    }
    $("#switchid_select").change(function(){
        plot_pie();
        var switchid = document.getElementById("switchid_select").value;
        var portid = document.getElementById("portid_select").value;
        var device = "s"+switchid+"-eth"+portid;
        document.getElementById("historyqosvariety_pie").innerHTML="端口"+device+"的历史服务质量变化剧烈程度对比——饼状图";
    });
    $("#portid_select").change(function(){
        plot_pie();
        var switchid = document.getElementById("switchid_select").value;
        var portid = document.getElementById("portid_select").value;
        var device = "s"+switchid+"-eth"+portid;
        document.getElementById("historyqosvariety_pie").innerHTML="端口"+device+"的历史服务质量变化剧烈程度对比——饼状图";
    });
    window.setInterval(plot_pie,1000);

});
//flot table
$(function(){
    function refresh_table(){
        var switchid = document.getElementById("switchid_select").value;
        var portid = document.getElementById("portid_select").value;
        var device = "s"+switchid+"-eth"+portid;
        $("#historyqoslist_table tbody").html("");
        var time,
            bandwidth,
            delay,
            jitter,
            loss,
            latency;
        $.ajax({
            url:"/wm/qosoverlldp/webgui/deviceqoshistorylist/"+device+"/json",
            datatype:"json",
            async:false,
            success:function(data){
                time=data["visualtime"];
                bandwidth=data["visualbandwidth"];
                delay=data["visualdelay"];
                jitter=data["visualjitter"];
                loss=data["loss"];
                latency=data["visuallatency"];
            }
        });
        $("#historyqoslist_table tbody").append("<tr><td>"+device+"</td><td>bandwidth</td><td>delay</td><td>jitter</td>" +
            "<td>loss</td><td>latency</td></tr>");
        for(var i=bandwidth.length-1;i>=0;i--){
            $("#historyqoslist_table tbody").append("<tr><td>"+time[i]+"</td><td>"+bandwidth[i]+"</td><td>"+delay[i]+"</td><td>"+jitter[i]+"</td>" +
                "<td>"+loss[i]+"</td><td>"+latency[i]+"</td></tr>");
        }
    }
    refresh_table();
    var switchid = document.getElementById("switchid_select").value;
    var portid = document.getElementById("portid_select").value;
    var device = "s"+switchid+"-eth"+portid;
    document.getElementById("historyqosvariety_table").innerHTML="端口"+device+"的历史服务质量数据——表格";
    var flot_line_chart_table = document.getElementById("flot-line-chart-table");
    flot_line_chart_table.onmouseover=function(){
        refresh_table();
        var switchid = document.getElementById("switchid_select").value;
        var portid = document.getElementById("portid_select").value;
        var device = "s"+switchid+"-eth"+portid;
        document.getElementById("historyqosvariety_table").innerHTML="端口"+device+"的历史服务质量数据——表格";
    }
    $("#switchid_select").change(function(){
        refresh_table()
        var switchid = document.getElementById("switchid_select").value;
        var portid = document.getElementById("portid_select").value;
        var device = "s"+switchid+"-eth"+portid;
        document.getElementById("historyqosvariety_table").innerHTML="端口"+device+"的历史服务质量数据——表格";
    });
    $("#portid_select").change(function(){
        refresh_table()
        var switchid = document.getElementById("switchid_select").value;
        var portid = document.getElementById("portid_select").value;
        var device = "s"+switchid+"-eth"+portid;
        document.getElementById("historyqosvariety_table").innerHTML="端口"+device+"的历史服务质量数据——表格";
    });
    window.setInterval(refresh_table,1000);
});