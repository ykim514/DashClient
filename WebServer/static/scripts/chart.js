"use strict";

var MAX_WIDTH = 5;
var domain = 'http://203.252.180.194/';
var cpuObject = {};
var netObject = {};
var diskObject = {};

function cpustat() {
  $.ajax(domain + 'cpustat').done(function(res) {
    var usage = eval(res.replace(/\s{2,}/g, ' '))[0].split(' ');
    var totalTime = parseInt(usage[1]) + parseInt(usage[2]) + parseInt(usage[3]) + parseInt(usage[4]);
    var usingTime = parseInt(usage[1]) + parseInt(usage[2]) + parseInt(usage[3]);

    if (!cpuObject.totalTime) {
      cpuObject.totalTime = totalTime;
    }

    if (!cpuObject.usingTime) {
      cpuObject.usingTime = usingTime;
    }

    var percent = 0.0;

    if (totalTime - cpuObject.totalTime > 0) {
      percent = (usingTime - cpuObject.usingTime) / (totalTime - cpuObject.totalTime);
    }

    percent = parseInt(percent * 10000) / 100;

    if (cpuObject.chart.datasets[0].points.length == MAX_WIDTH) {
      cpuObject.chart.removeData();
    }

    cpuObject.chart.addData([percent, 100], new Date().toString('HH:mm'));
    cpuObject.totalTime = totalTime;
    cpuObject.usingTime = usingTime;
  });
}

function netstat(dev) {
  $.ajax(domain + 'netstat?dev=' + dev).done(function(res) {
    var usage = eval(res.replace(/\s{2,}/g, ' '))[0].split(' ');

    if (!netObject.rx) {
      netObject.rx = parseInt(usage[2]);
    }

    if (!netObject.tx) {
      netObject.tx = parseInt(usage[10]);
    }

    if (netObject.chart.datasets[0].points.length == MAX_WIDTH) {
      netObject.chart.removeData();
    }

    var rx = parseInt(usage[2]) - netObject.rx;
    var tx = parseInt(usage[10]) - netObject.tx;

    netObject.chart.addData([rx, tx], new Date().toString('HH:mm'));
    netObject.rx = parseInt(usage[2]);
    netObject.tx = parseInt(usage[10]);
  });
}

function diskstat(dev) {
  $.ajax(domain + 'diskstat?dev=' + dev).done(function(res) {
    var usage = eval(res.replace(/\s{2,}/g, ' '))[0].split(' ');

    if (!diskObject.read) {
      diskObject.read = parseInt(usage[1]);
    }

    if (!diskObject.write) {
      diskObject.write = parseInt(usage[5]);
    }

    var read = parseInt(usage[1]) - diskObject.read;
    var write = parseInt(usage[5]) - diskObject.write;

    if (diskObject.chart.datasets[0].points.length == MAX_WIDTH) {
      diskObject.chart.removeData();
    }

    diskObject.chart.addData([read, write], new Date().toString('HH:mm'));
    diskObject.read = parseInt(usage[1]);
    diskObject.write = parseInt(usage[5]);
  });
}

function defaultDataSet() {
  return {
    labels: [''],
    datasets: [{
      label: '',
      fillColor: "rgba(151,187,205,0.2)",
      strokeColor: "rgba(151,187,205,1)",
      pointColor: "rgba(151,187,205,1)",
      pointStrokeColor: "#fff",
      pointHighlightFill: "#fff",
      pointHighlightStroke: "rgba(151,187,205,1)",
      data: [0, ],
    }, {
      label: "",
      fillColor: "rgba(220,220,220,0.2)",
      strokeColor: "rgba(220,220,220,1)",
      pointColor: "rgba(220,220,220,1)",
      pointStrokeColor: "#fff",
      pointHighlightFill: "#fff",
      pointHighlightStroke: "rgba(220,220,220,1)",
      data: [100, ],
      },
    ],
  };
}

function makeChart(object, parentNode, data) {
  var canvas = $('<canvas></canvas>');
  var context = canvas[0].getContext('2d');
  canvas.css('width', '320px');
  canvas.css('height', '240px');
  canvas.css('margin', '0 20px 0 0');
  parentNode.append(canvas);
  object.chart = new Chart(context).Line(data);
}

$(function() {
  var data = defaultDataSet();
  data.datasets[0].label = 'CPU Usage';
  data.datasets[1].label = 'CPU Max Usage';
  makeChart(cpuObject, $('#divMonitoring'), data);
  setInterval(function() { cpustat(); }, 5000);

  data.datasets[0].label = 'Network Rx';
  data.datasets[0].data = [0, ];
  data.datasets[1].label = 'Network Tx';
  data.datasets[1].data = [0, ];
  makeChart(netObject, $('#divMonitoring'), data);
  setInterval(function() { netstat('eth0'); }, 5000);

  data.datasets[0].label = 'Disk Read';
  data.datasets[0].data = [0, ];
  data.datasets[1].label = 'Disk Write';
  data.datasets[1].data = [0, ];
  makeChart(diskObject, $('#divMonitoring'), data);
  setInterval(function() { diskstat('sda') }, 5000);
});
