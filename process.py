import numpy as np
import xarray as xr
import re
from pathlib import Path
import collections


def distance(val, ref):
    return abs(ref - val)


vectDistance = np.vectorize(distance)


def cmap_xmap(function, cmap):
    """ Applies function, on the indices of colormap cmap. Beware, function
    should map the [0, 1] segment to itself, or you are in for surprises.

    See also cmap_xmap.
    """
    cdict = cmap._segmentdata
    function_to_map = lambda x: (function(x[0]), x[1], x[2])
    for key in ('red', 'green', 'blue'):
        cdict[key] = map(function_to_map, cdict[key])
    #        cdict[key].sort()
    #        assert (cdict[key][0]<0 or cdict[key][-1]>1), "Resulting indices extend out of the [0, 1] segment."
    return matplotlib.colors.LinearSegmentedColormap('colormap', cdict, 1024)


def getClosest(sortedMatrix, column, val):
    while len(sortedMatrix) > 3:
        half = int(len(sortedMatrix) / 2)
        sortedMatrix = sortedMatrix[-half - 1:] if sortedMatrix[half, column] < val else sortedMatrix[: half + 1]
    if len(sortedMatrix) == 1:
        result = sortedMatrix[0].copy()
        result[column] = val
        return result
    else:
        safecopy = sortedMatrix.copy()
        safecopy[:, column] = vectDistance(safecopy[:, column], val)
        minidx = np.argmin(safecopy[:, column])
        safecopy = safecopy[minidx, :].A1
        safecopy[column] = val
        return safecopy


def convert(column, samples, matrix):
    return np.matrix([getClosest(matrix, column, t) for t in samples])


def valueOrEmptySet(k, d):
    return (d[k] if isinstance(d[k], set) else {d[k]}) if k in d else set()


def mergeDicts(d1, d2):
    """
    Creates a new dictionary whose keys are the union of the keys of two
    dictionaries, and whose values are the union of values.

    Parameters
    ----------
    d1: dict
        dictionary whose values are sets
    d2: dict
        dictionary whose values are sets

    Returns
    -------
    dict
        A dict whose keys are the union of the keys of two dictionaries,
    and whose values are the union of values

    """
    res = {}
    for k in d1.keys() | d2.keys():
        res[k] = valueOrEmptySet(k, d1) | valueOrEmptySet(k, d2)
    return res


def extractCoordinates(filename):
    """
    Scans the header of an Alchemist file in search of the variables.

    Parameters
    ----------
    filename : str
        path to the target file
    mergewith : dict
        a dictionary whose dimensions will be merged with the returned one

    Returns
    -------
    dict
        A dictionary whose keys are strings (coordinate name) and values are
        lists (set of variable values)

    """
    with open(filename, 'r') as file:
        #        regex = re.compile(' (?P<varName>[a-zA-Z._-]+) = (?P<varValue>[-+]?\d*\.?\d+(?:[eE][-+]?\d+)?),?')
        regex = r"(?P<varName>[a-zA-Z._-]+) = (?P<varValue>(?:\[[^\]]*\]|[^,]*)),?"
        dataBegin = r"\d"
        is_float = r"[-+]?\d*\.?\d+(?:[eE][-+]?\d+)?"
        for line in file:
            match = re.findall(regex, line.replace('Infinity', '1e30000'))
            if match:
                return {
                    var: float(value) if re.match(is_float, value)
                    else bool(re.match(r".*?true.*?", value.lower())) if re.match(r".*?(true|false).*?", value.lower())
                    else value.replace('\n', '')
                    for var, value in match
                }
            elif re.match(dataBegin, line[0]):
                return {}


def extractVariableNames(filename):
    """
    Gets the variable names from the Alchemist data files header.

    Parameters
    ----------
    filename : str
        path to the target file

    Returns
    -------
    list of list
        A matrix with the values of the csv file

    """
    with open(filename, 'r') as file:
        dataBegin = re.compile(r'\d')
        lastHeaderLine = ''
        for line in file:
            if dataBegin.match(line[0]):
                break
            else:
                lastHeaderLine = line
        if lastHeaderLine:
            regex = re.compile(r' (?P<varName>\S+)')
            return regex.findall(lastHeaderLine)
        return []


def openCsv(path):
    """
    Converts an Alchemist export file into a list of lists representing the matrix of values.

    Parameters
    ----------
    path : str
        path to the target file

    Returns
    -------
    list of list
        A matrix with the values of the csv file

    """
    regex = re.compile(r'\d')
    with open(path, 'r') as file:
        lines = filter(lambda x: regex.match(x[0]), file.readlines())
        return [[float(x) for x in line.split()] for line in lines]


def beautifyValue(v):
    """
    Converts an object to a better version for printing, in particular:
        - if the object converts to float, then its float value is used
        - if the object can be rounded to int, then the int value is preferred

    Parameters
    ----------
    v : object
        the object to try to beautify

    Returns
    -------
    object or float or int
        the beautified value
    """
    try:
        v = float(v)
        if v.is_integer():
            return int(v)
        return v
    except:
        return v


if __name__ == '__main__':
    # CONFIGURE SCRIPT
    # Where to find Alchemist data files
    directory = 'data'
    # Where to save charts
    output_directory = 'charts'
    # How to name the summary of the processed data
    pickleOutput = 'data_summary'
    # Experiment prefixes: one per experiment (root of the file name)
    experiments = ['dynamic']
    floatPrecision = '{: 0.3f}'
    # time management
    minTime = 0
    maxTime = int(16 * 3600) - minTime
    # Number of time samples
    timeSamples = int((maxTime - minTime) / 60)
    timeColumnName = 'time'
    logarithmicTime = False
    # One or more variables are considered random and "flattened"
    # seedVars = ['Seed']
    seedVars = []


    # Label mapping
    class Measure:
        def __init__(self, description, unit=None):
            self.__description = description
            self.__unit = unit

        def description(self):
            return self.__description

        def unit(self):
            return '' if self.__unit is None else f'({self.__unit})'

        def derivative(self, new_description=None, new_unit=None):
            def cleanMathMode(s):
                return s[1:-1] if s[0] == '$' and s[-1] == '$' else s

            def deriveString(s):
                return r'$d ' + cleanMathMode(s) + r'/{dt}$'

            def deriveUnit(s):
                return f'${cleanMathMode(s)}' + '/{s}$' if s else None

            result = Measure(
                new_description if new_description else deriveString(self.__description),
                new_unit if new_unit else deriveUnit(self.__unit),
            )
            return result

        def __str__(self):
            return f'{self.description()} {self.unit()}'


    centrality_label = 'H_a(x)'


    def expected(x):
        return r'\mathbf{E}[' + x + ']'


    def stdev_of(x):
        return r'\sigma{}[' + x + ']'


    def mse(x):
        return 'MSE[' + x + ']'


    def cardinality(x):
        return r'\|' + x + r'\|'


    labels_thresholds = {
        'nodeCount': Measure(r'$n$', 'nodes'),
        'harmonicCentrality[Mean]': Measure(f'${expected("H(x)")}$'),
        'meanNeighbors': Measure(f'${expected(cardinality("N"))}$', 'nodes'),
        'speed': Measure(r'$\|\vec{v}\|$', r'$m/s$'),
        'msqer@harmonicCentrality[Max]': Measure(r'$\max{(' + mse(centrality_label) + ')}$'),
        'msqer@harmonicCentrality[Min]': Measure(r'$\min{(' + mse(centrality_label) + ')}$'),
        'msqer@harmonicCentrality[Mean]': Measure(f'${expected(mse(centrality_label))}$'),
        'msqer@harmonicCentrality[StandardDeviation]': Measure(f'${stdev_of(mse(centrality_label))}$'),
        'org:protelis:tutorial:distanceTo[max]': Measure(r'$m$', 'max distance'),
        'org:protelis:tutorial:distanceTo[mean]': Measure(r'$m$', 'mean distance'),
        'org:protelis:tutorial:distanceTo[min]': Measure(r'$m$', ',min distance'),
    }


    def derivativeOrMeasure(variable_name):
        if variable_name.endswith('dt'):
            return labels_thresholds.get(variable_name[:-2], Measure(variable_name)).derivative()
        return Measure(variable_name)


    def label_for(variable_name):
        return labels_thresholds.get(variable_name, derivativeOrMeasure(variable_name)).description()


    def unit_for(variable_name):
        return str(labels_thresholds.get(variable_name, derivativeOrMeasure(variable_name)))


    # Setup libraries
    np.set_printoptions(formatter={'float': floatPrecision.format})
    # Read the last time the data was processed, reprocess only if new data exists, otherwise just load
    import pickle
    import os

    if os.path.exists(directory):
        newestFileTime = max([os.path.getmtime(directory + '/' + file) for file in os.listdir(directory)], default=0.0)
        try:
            lastTimeProcessed = pickle.load(open('timeprocessed', 'rb'))
        except:
            lastTimeProcessed = -1
        shouldRecompute = not os.path.exists(".skip_data_process") and newestFileTime != lastTimeProcessed
        if not shouldRecompute:
            try:
                means = pickle.load(open(pickleOutput + '_mean', 'rb'))
                stdevs = pickle.load(open(pickleOutput + '_std', 'rb'))
            except:
                shouldRecompute = True
        if shouldRecompute:
            timefun = np.logspace if logarithmicTime else np.linspace
            means = {}
            stdevs = {}
            for experiment in experiments:
                # Collect all files for the experiment of interest
                import fnmatch

                allfiles = filter(lambda file: fnmatch.fnmatch(file, experiment + '_*.csv'), os.listdir(directory))
                allfiles = [directory + '/' + name for name in allfiles]
                allfiles.sort()
                # From the file name, extract the independent variables
                dimensions = {}
                for file in allfiles:
                    dimensions = mergeDicts(dimensions, extractCoordinates(file))
                dimensions = {k: sorted(v) for k, v in dimensions.items()}
                # Add time to the independent variables
                dimensions[timeColumnName] = range(0, timeSamples)
                # Compute the matrix shape
                shape = tuple(len(v) for k, v in dimensions.items())
                # Prepare the Dataset
                dataset = xr.Dataset()
                for k, v in dimensions.items():
                    dataset.coords[k] = v
                if len(allfiles) == 0:
                    print("WARNING: No data for experiment " + experiment)
                    means[experiment] = dataset
                    stdevs[experiment] = xr.Dataset()
                else:
                    varNames = extractVariableNames(allfiles[0])
                    for v in varNames:
                        if v != timeColumnName:
                            novals = np.ndarray(shape)
                            novals.fill(float('nan'))
                            dataset[v] = (dimensions.keys(), novals)
                    # Compute maximum and minimum time, create the resample
                    timeColumn = varNames.index(timeColumnName)
                    allData = {file: np.matrix(openCsv(file)) for file in allfiles}
                    computeMin = minTime is None
                    computeMax = maxTime is None
                    if computeMax:
                        maxTime = float('-inf')
                        for data in allData.values():
                            maxTime = max(maxTime, data[-1, timeColumn])
                    if computeMin:
                        minTime = float('inf')
                        for data in allData.values():
                            minTime = min(minTime, data[0, timeColumn])
                    timeline = timefun(minTime, maxTime, timeSamples)
                    # Resample
                    for file in allData:
                        #                    print(file)
                        allData[file] = convert(timeColumn, timeline, allData[file])
                    # Populate the dataset
                    for file, data in allData.items():
                        dataset[timeColumnName] = timeline
                        for idx, v in enumerate(varNames):
                            if v != timeColumnName:
                                darray = dataset[v]
                                experimentVars = extractCoordinates(file)
                                darray.loc[experimentVars] = data[:, idx].A1
                    # Fold the dataset along the seed variables, producing the mean and stdev datasets
                    mergingVariables = [seed for seed in seedVars if seed in dataset.coords]
                    means[experiment] = dataset.mean(dim=mergingVariables, skipna=True)
                    stdevs[experiment] = dataset.std(dim=mergingVariables, skipna=True)
            # Save the datasets
            pickle.dump(means, open(pickleOutput + '_mean', 'wb'), protocol=-1)
            pickle.dump(stdevs, open(pickleOutput + '_std', 'wb'), protocol=-1)
            pickle.dump(newestFileTime, open('timeprocessed', 'wb'))
    else:
        means = {experiment: xr.Dataset() for experiment in experiments}
        stdevs = {experiment: xr.Dataset() for experiment in experiments}

    # QUICK CHARTING

    import matplotlib
    import matplotlib.pyplot as plt
    import matplotlib.cm as cmx

    matplotlib.rcParams.update({'axes.titlesize': 12})
    matplotlib.rcParams.update({'axes.labelsize': 10})


    def make_line_chart(
            xdata,
            ydata,
            title=None,
            ylabel=None,
            xlabel=None,
            colors=None,
            linewidth=1,
            error_alpha=0.2,
            figure_size=(6, 4)
    ):
        fig = plt.figure(figsize=figure_size)
        ax = fig.add_subplot(1, 1, 1)
        ax.set_title(title)
        ax.set_xlabel(xlabel)
        ax.set_ylabel(ylabel)
        #        ax.set_ylim(0)
        #        ax.set_xlim(min(xdata), max(xdata))
        index = 0
        for (label, (data, error)) in ydata.items():
            #            print(f'plotting {data}\nagainst {xdata}')
            lines = ax.plot(xdata, data, label=label, color=colors(index / (len(ydata) - 1)) if colors else None,
                            linewidth=linewidth)
            index += 1
            if error is not None:
                last_color = lines[-1].get_color()
                ax.fill_between(
                    xdata,
                    data + error,
                    data - error,
                    facecolor=last_color,
                    alpha=error_alpha,
                )
        return (fig, ax)


    def generate_all_charts(means, errors=None, basedir=''):
        viable_coords = {coord for coord in means.coords if means[coord].size > 1}
        for comparison_variable in viable_coords - {timeColumnName}:
            mergeable_variables = viable_coords - {timeColumnName, comparison_variable}
            for current_coordinate in mergeable_variables:
                merge_variables = mergeable_variables - {current_coordinate}
                merge_data_view = means.mean(dim=merge_variables, skipna=True)
                merge_error_view = errors.mean(dim=merge_variables, skipna=True)
                for current_coordinate_value in merge_data_view[current_coordinate].values:
                    beautified_value = beautifyValue(current_coordinate_value)
                    for current_metric in merge_data_view.data_vars:
                        title = f'{label_for(current_metric)} for diverse {label_for(comparison_variable)} when {label_for(current_coordinate)}={beautified_value}'
                        for withErrors in [True, False]:
                            fig, ax = make_line_chart(
                                title=title,
                                xdata=merge_data_view[timeColumnName],
                                xlabel=unit_for(timeColumnName),
                                ylabel=unit_for(current_metric),
                                ydata={
                                    beautifyValue(label): (
                                        merge_data_view.sel(selector)[current_metric],
                                        merge_error_view.sel(selector)[current_metric] if withErrors else 0
                                    )
                                    for label in merge_data_view[comparison_variable].values
                                    for selector in
                                    [{comparison_variable: label, current_coordinate: current_coordinate_value}]
                                },
                            )
                            ax.set_xlim(minTime, maxTime)
                            ax.legend()
                            fig.tight_layout()
                            by_time_output_directory = f'{output_directory}/{basedir}/{comparison_variable}'
                            Path(by_time_output_directory).mkdir(parents=True, exist_ok=True)
                            figname = f'{comparison_variable}_{current_metric}_{current_coordinate}_{beautified_value}{"_err" if withErrors else ""}'
                            for symbol in r".[]\/@:":
                                figname = figname.replace(symbol, '_')
                            fig.savefig(f'{by_time_output_directory}/{figname}.pdf')
                            plt.close(fig)


    for experiment in experiments:
        current_experiment_means = means[experiment]
        current_experiment_errors = stdevs[experiment]
        # generate_all_charts(current_experiment_means, current_experiment_errors, basedir = f'{experiment}/all')

    # Custom charting
    import math
    import matplotlib.ticker as mticker
    import seaborn as sns
    import seaborn.objects as so
    import pandas as pd
    from seaborn import axes_style

    os.makedirs(f"{output_directory}/custom", exist_ok=True)

    plt.rc('text.latex', preamble=r'\usepackage{amsmath,amssymb,amsfonts,amssymb,graphicx}')
    plt.rcParams.update({"text.usetex": True})

    # sns.set(font_scale=2)
    so.Plot.config.theme.update(axes_style("whitegrid"))
    so.Plot.config.theme["font.size"] = 10
    so.Plot.config.theme["axes.titlesize"] = 18
    so.Plot.config.theme["axes.labelsize"] = 16
    so.Plot.config.theme["legend.fontsize"] = 16
    # setup viridis seaborn

    thresholds = [r'$\Updownarrow_{0}$', r'$\Updownarrow_{10}$', r'$\Updownarrow_{100}$', r'$\Updownarrow_{20}$', r'$\Updownarrow_{30}$', r'$\Updownarrow_{40}$']
    thresholds_ordered = [r'$\Updownarrow_{0}$', r'$\Updownarrow_{10}$', r'$\Updownarrow_{20}$', r'$\Updownarrow_{30}$', r'$\Updownarrow_{40}$', r'$\Updownarrow_{100}$']
    ordered_policies = ['smartphone', 'hybrid', 'wearable']

    dynamic_dataset = means['dynamic']
    dynamic_dataset.coords['Thresholds'] = thresholds
    dynamic_dataset = dynamic_dataset.reindex(Thresholds=thresholds_ordered)
    dynamic_dataset = dynamic_dataset.reindex(SwapPolicy=ordered_policies)

    window_in_seconds = 1800  # 30 minutes window
    travel_distance = dynamic_dataset['TraveledDistance[mean]'].to_dataframe()
    travel_distance.rename({'TraveledDistance[mean]': 'TraveledDistance'}, axis=1, inplace=True)
    rows_per_window = math.ceil(window_in_seconds / (dynamic_dataset['time'].diff(dim='time').mean()))
    travel_distance = travel_distance.rolling(window=rows_per_window).apply(lambda x: x.iloc[-1] - x.iloc[0])

    travel_plot = (
        so.Plot(travel_distance, x='time', y='TraveledDistance', color='Thresholds')
        .add(so.Line(), so.Agg())
        .add(so.Band())
        .facet("SwapPolicy")
        .layout(engine='tight', size=(20, 4))
        .scale(color='viridis')
        .label(
            x="Time (s)",
            y="Traveled Distance (m)",
            title="Sensor Allocation = {}".format
        )
        .plot()
    )
    travel_plot.save(f"{output_directory}/custom/travel_distance.pdf", bbox_inches="tight")
    travel_plot.save(f"{output_directory}/custom/travel_distance.svg", bbox_inches="tight")

    max_traveled_distance = dynamic_dataset['TraveledDistance[mean]'].max(dim='time').to_dataframe()
    max_traveled_distance.rename({'TraveledDistance[mean]': 'TraveledDistance'}, axis=1, inplace=True)
    max_traveled_distance_plot = (
        so.Plot(max_traveled_distance, x='Thresholds', y='TraveledDistance', color='SwapPolicy')
        .add(so.Bar(), so.Agg(), so.Dodge())
        .add(so.Range(), so.Est(errorbar="sd"), so.Dodge())
        # .limit(y=(25000, None))
        .layout(engine='tight')
        .scale(color='viridis')
        .label(y="Traveled Distance (m)")
        .plot()
    )
    max_traveled_distance_plot.save(f"{output_directory}/custom/max_traveled_distance.pdf", bbox_inches="tight")
    max_traveled_distance_plot.save(f"{output_directory}/custom/max_traveled_distance.svg", bbox_inches="tight")
    # End plot traveled distance ---------------------------------------------------------------------------------------

    cloud_cost = dynamic_dataset['CloudCost[sum]']
    cloud_cost = cloud_cost.max(dim='time').to_dataframe()
    cloud_cost = cloud_cost / (maxTime / 3600)
    cloud_cost.rename({'CloudCost[sum]': 'CloudCost'}, axis=1, inplace=True)
    cloud_cost_plot = (
        so.Plot(cloud_cost, x='Thresholds', y='CloudCost', color='SwapPolicy')
        .add(so.Bar(), so.Agg(), so.Dodge())
        .add(so.Range(), so.Est(errorbar="sd"), so.Dodge())
        .layout(engine='tight')
        .scale(color='viridis')
        .label(y=r"$\$_{cloud} (\$/h)$")
        .plot()
    )
    cloud_cost_plot.save(f"{output_directory}/custom/cloud_cost.pdf", bbox_inches="tight")
    cloud_cost_plot.save(f"{output_directory}/custom/cloud_cost.svg", bbox_inches="tight")
    # End plot cloud cost ----------------------------------------------------------------------------------------------

    qos = dynamic_dataset[['TraveledDistance[mean]', 'CloudCost[sum]', 'WearableCharging[mean]', 'SmartphoneCharging[mean]']]
    qos = qos.to_dataframe()
    traveled_window = qos['TraveledDistance[mean]'].rolling(window=rows_per_window).apply(lambda x: x.iloc[-1] - x.iloc[0])
    cost_window = qos['CloudCost[sum]'].rolling(window=rows_per_window).apply(lambda x: x.iloc[-1] - x.iloc[0])
    smartphone_charging = qos['SmartphoneCharging[mean]'].rolling(window=rows_per_window).apply(lambda x: x.iloc[-1] - x.iloc[0])
    wearable_charging = qos['WearableCharging[mean]'].rolling(window=rows_per_window).apply(lambda x: x.iloc[-1] - x.iloc[0])
    qos['QoS'] = (traveled_window * (1 - smartphone_charging) * (1 - wearable_charging)) / cost_window
    qos_plot = (
        so.Plot(qos, x='time', y='QoS', color='Thresholds')
        .add(so.Line(), so.Agg())
        .add(so.Band())
        .facet("SwapPolicy")
        .layout(engine='tight', size=(20, 4))
        .scale(color='viridis')
        .label(
            x="Time (s)",
            y=r"QoS (m/\$)",
            title="Sensor Allocation = {}".format
        )
        .plot()
    )
    qos_plot.save(f"{output_directory}/custom/qos.pdf", bbox_inches="tight")
    qos_plot.save(f"{output_directory}/custom/qos.svg", bbox_inches="tight")

    qos_max = dynamic_dataset.max(dim='time').to_dataframe()
    qos_max['QoS'] = qos_max['TraveledDistance[mean]'] / qos_max['CloudCost[sum]']

    qos_bar = (
        so.Plot(qos_max, x='Thresholds', y='QoS', color='SwapPolicy')
        .add(so.Bar(), so.Agg(), so.Dodge())
        .add(so.Range(), so.Est(errorbar="sd"), so.Dodge())
        .layout(engine='tight')
        .scale(color='viridis')
        .label(y=r"QoS (m/\$)")
        .plot()
    )
    qos_bar.save(f"{output_directory}/custom/qos_bar.pdf", bbox_inches="tight")

    charging = dynamic_dataset[['SmartphoneCharging[mean]', 'WearableCharging[mean]']].to_dataframe()
    charging.rename({'SmartphoneCharging[mean]': 'SmartphoneCharging'}, axis=1, inplace=True)
    charging.rename({'WearableCharging[mean]': 'WearableCharging'}, axis=1, inplace=True)
    charging['Charging'] = (1 - charging['SmartphoneCharging']) * (1 - charging['WearableCharging'])
    charging_plot = (
        so.Plot(charging, x='Thresholds', y='Charging', color='SwapPolicy')
        .add(so.Bar(), so.Agg(), so.Dodge())
        .add(so.Range(), so.Est(errorbar="sd"), so.Dodge())
        .layout(engine='tight')
        .scale(color='viridis')
        .label(
            x="Thresholds",
            y=r"Operative Devices (\%)",
        )
        .plot()
    )
    charging_plot.save(f"{output_directory}/custom/charging.pdf", bbox_inches="tight")
    charging_plot.save(f"{output_directory}/custom/charging.svg", bbox_inches="tight")
    # End plot QoS -----------------------------------------------------------------------------------------------------

    power_consumption = dynamic_dataset.sum(dim='time')[['SmartphonePower[mean]', 'WearablePower[mean]', 'CloudPower[mean]']].to_dataframe()
    power_consumption.rename({'SmartphonePower[mean]': 'SmartphonePowerConsumption'}, axis=1, inplace=True)
    power_consumption.rename({'WearablePower[mean]': 'WearablePowerConsumption'}, axis=1, inplace=True)
    power_consumption.rename({'CloudPower[mean]': 'CloudPower'}, axis=1, inplace=True)
    power_consumption['PowerConsumption'] = power_consumption['SmartphonePowerConsumption'] + power_consumption['WearablePowerConsumption'] + power_consumption['CloudPower']
    power_consumption['PowerConsumption'] = power_consumption['PowerConsumption'] / (maxTime / 3600)

    power_consumption_plot = (
        so.Plot(power_consumption, x='Thresholds', y='PowerConsumption', color='SwapPolicy')
        .add(so.Bar(), so.Agg(), so.Dodge())
        .add(so.Range(), so.Est(errorbar="sd"), so.Dodge())
        .layout(engine='tight')
        .scale(color='viridis')
        .label(
            x="Thresholds",
            y=r"$P_{system} (W/h)$",
        )
        .plot()
    )
    power_consumption_plot.save(f"{output_directory}/custom/power_consumption.pdf", bbox_inches="tight")
    power_consumption_plot.save(f"{output_directory}/custom/power_consumption.svg", bbox_inches="tight")
    # End plot power consumption ---------------------------------------------------------------------------------------

    # cost_wearable = dynamic_dataset[['PercentageSensorInWearable', 'CloudCost[sum]']].to_dataframe()
    # cost_wearable.rename({'CloudCost[sum]': 'CloudCost'}, axis=1, inplace=True)
    # cost_wearable_plot = (
    #     so.Plot(cost_wearable, x='PercentageSensorInWearable', y='CloudCost', color='Thresholds')
    #     .add(so.Line(), so.Agg())
    #     .add(so.Band())
    #     .layout(engine='tight', size=(20, 4))
    #     .facet("SwapPolicy")
    #     .scale(color='viridis')
    #     .label(
    #         x="Percentage of Sensors in Wearable",
    #         y=r"Cloud Cost (\$)",
    #         title="Sensor Allocation = {}".format
    #     )
    #     .plot()
    # )
    # cost_wearable_plot.save(f"{output_directory}/custom/cost_wearable.pdf", bbox_inches="tight")
    # End plot cost wearable -------------------------------------------------------------------------------------------

    charging_time = dynamic_dataset.max(dim='time')[['SmartphoneRechargeTime[mean]']].to_dataframe()
    charging_time.rename({'SmartphoneRechargeTime[mean]': 'ChargingTime'}, axis=1, inplace=True)
    charging_time = charging_time / 60

    charging_time_plot = (
        so.Plot(charging_time, x='Thresholds', y='ChargingTime', color='SwapPolicy')
        .add(so.Bar(), so.Agg(), so.Dodge())
        .add(so.Range(), so.Est(errorbar="sd"), so.Dodge())
        .layout(engine='tight')
        .scale(color='viridis')
        .label(
            x="Thresholds",
            y=r"Time Spent Recharging (minutes)",
        )
        .plot()
    )
    charging_time_plot.save(f"{output_directory}/custom/charging_time.pdf", bbox_inches="tight")
    charging_time_plot.save(f"{output_directory}/custom/charging_time.svg", bbox_inches="tight")
    # End plot charging time -------------------------------------------------------------------------------------------

    performance = dynamic_dataset.max(dim='time')[['SmartphoneRechargeTime[mean]', 'CloudCost[sum]']].to_dataframe()
    performance.rename({'SmartphoneRechargeTime[mean]': 'ChargingTime'}, axis=1, inplace=True)
    performance.rename({'CloudCost[sum]': 'CloudCost'}, axis=1, inplace=True)
    performance['Performance'] = (1 - (performance['ChargingTime'] / maxTime)) / performance['CloudCost']

    performance_plot = (
        so.Plot(performance, x='Thresholds', y='Performance', color='SwapPolicy')
        .add(so.Bar(), so.Agg(), so.Dodge())
        .add(so.Range(), so.Est(errorbar="sd"), so.Dodge())
        .layout(engine='tight')
        .scale(color='viridis')
        .label(
            x="Thresholds",
            y=r"Performance",
        )
        .plot()
    )
    performance_plot.save(f"{output_directory}/custom/performance.pdf", bbox_inches="tight")
    performance_plot.save(f"{output_directory}/custom/performance.svg", bbox_inches="tight")
    # End plot performance -------------------------------------------------------------------------------------------
