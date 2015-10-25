(function (timespanSelection, graphFactory, dataSource, jobColors) {
    // Roughly following http://bl.ocks.org/mbostock/4063269
    var jobCount = 5,
        borderWidthInPx = 30,
        worstFailureRatio = 0.25;

    var failRatio = function (job) {
        var failCount = job.failedCount || 0;
        return failCount / job.totalCount;
    };

    var failedBuildsAsBubbles = function (pipeline) {
        return Object.keys(pipeline)
            .filter(function (jobName) {
                return pipeline[jobName].failedCount > 0;
            })
            .map(function (jobName) {
                var failedCount = pipeline[jobName].failedCount,
                    ratio = failRatio(pipeline[jobName]);
                return {
                    name: jobName,
                    title: jobName + '\n\n' + failedCount + ' failures\n' + (ratio * 100).toFixed(0) + '% of the time',
                    failRatio: ratio,
                    value: failedCount
                };
            });
    };

    var failedCount = function (job) {
        return job.failedCount || 0;
    };

    var selectMostFailed = function (pipeline, n) {
        var jobNames = Object.keys(pipeline);
        jobNames.sort(function (jobA, jobB) {
            return failedCount(pipeline[jobA]) - failedCount(pipeline[jobB]);
        });

        var selectedPipeline = {};
        jobNames.slice(-n).forEach(function (job) {
            selectedPipeline[job] = pipeline[job];
        });
        return selectedPipeline;
    };

    var bubble = d3.layout.pack()
            .sort(null)
            .size([graphFactory.size, graphFactory.size])
            .padding(1.5),
        noGrouping = function (bubbleNodes) {
            return bubbleNodes.filter(function(d) { return d.depth > 0; });
        };

    var colorScale = function (maxDomain) {
        return d3.scale.linear()
            .domain([0, maxDomain])
            .range(["white", d3.rgb("red").darker()])
            .interpolate(d3.interpolateLab);
    };

    var color = colorScale(worstFailureRatio);

    var renderData = function (root, svg) {
        var jobNames = Object.keys(root),
            jobColor = jobColors.colors(jobNames),
            failedBuilds = failedBuildsAsBubbles(selectMostFailed(root, jobCount));

        var selection = svg.selectAll("g")
                .data(noGrouping(bubble.nodes({children: failedBuilds})),
                      function(d) { return d.name; });

        selection
            .exit()
            .remove();

        var node = selection
                .enter()
                .append('g');

        node.append('title');
        node.append('circle')
            .attr("stroke-width", borderWidthInPx)
            .style("fill", function (d) {
                return jobColor(d.name);
            });
        node.append('text')
            .style("text-anchor", "middle")
            .each(function (d) {
                graphFactory.textWithLineBreaks(this, d.name.split(' '));
            });

        selection
            .transition()
            .attr("transform", function(d) { return "translate(" + d.x + "," + d.y + ")"; });

        selection.select('title')
            .text(function(d) { return d.title; });

        selection.select('circle')
            .transition()
            .attr("r", function (d) { return (d.r - borderWidthInPx / 2); })
            .style("stroke", function(d) { return color(d.failRatio); });
    };

    var timespanSelector = timespanSelection.create(timespanSelection.timespans.twoWeeks),
        graph = graphFactory.create({
            id: 'failedBuilds',
            headline: "Top 5 failed builds",
            description: "<h3>What needs most manual intervention? Where are the biggest quality issues? Where do we receive either not so valuable or actually very valuable feedback?</h3><i>Border color: failure ratio, inner color: job, diameter: number of failures</i>",
            csvUrl: "/jobs.csv",
            noDataReason: "provided the <code>outcome</code> of your builds",
            widgets: [timespanSelector.widget]
        });

    timespanSelector.load(function (selectedTimespan) {
        var fromTimestamp = timespanSelection.startingFromTimestamp(selectedTimespan);

        graph.loading();

        dataSource.load('/jobs?from=' + fromTimestamp, function (data) {
            graph.loaded();

            renderData(data, graph.svg);
        });
    });

}(timespanSelection, graphFactory, dataSource, jobColors));