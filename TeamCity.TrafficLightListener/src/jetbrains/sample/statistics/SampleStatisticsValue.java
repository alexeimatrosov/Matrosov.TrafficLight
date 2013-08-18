/*
 * Copyright (c) 2006-2013, JetBrains, s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.sample.statistics;

import java.math.BigDecimal;
import java.util.*;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.statistics.ChartSettings;
import jetbrains.buildServer.serverSide.statistics.ValueProviderRegistry;
import jetbrains.buildServer.serverSide.statistics.ValueType;
import jetbrains.buildServer.serverSide.statistics.build.BuildChartSettings;
import jetbrains.buildServer.serverSide.statistics.build.BuildDataStorage;
import jetbrains.buildServer.serverSide.statistics.build.BuildFinishAware;
import jetbrains.buildServer.serverSide.statistics.build.BuildValue;
import jetbrains.buildServer.util.filters.Filter;
import jetbrains.buildServer.util.filters.FilterUtil;
import jetbrains.buildServer.vcs.SVcsModification;
import jetbrains.buildServer.vcs.SelectPrevBuildPolicy;
import jetbrains.buildServer.web.openapi.PlaceId;
import jetbrains.buildServer.web.openapi.SimplePageExtension;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Sample statistics data provider
 */
public class SampleStatisticsValue implements ValueType, BuildFinishAware {

  private final ValueProviderRegistry myValueProviderRegistry;
  private final SBuildServer myServer;
  private final WebControllerManager myWebControllerManager;
  private final BuildDataStorage myStorage;

  public SampleStatisticsValue(final BuildDataStorage storage, final ValueProviderRegistry valueProviderRegistry,
                               final SBuildServer server, WebControllerManager manager) {
    myStorage = storage;
    myValueProviderRegistry = valueProviderRegistry;
    myServer = server;
    myWebControllerManager = manager;
  }

  public void register() {
    myValueProviderRegistry.registerValueProvider(this);
    SimplePageExtension statExt = new SimplePageExtension(
      myWebControllerManager, PlaceId.BUILD_CONF_STATISTICS_FRAGMENT, "samplePlugin", "sampleStatistics.jsp");
    statExt.register();
  }

  /**
   * Returns statistics data stored in general storage. Some other storage can be used here.
   * Also ValueType can compose result value from several other ValueTypes registered in system.
   *
   * @param chartSettings filter which has to be applied to returned values.
   * @return iterable collection of statistics values.
   */
  @NotNull
  public List<BuildValue> getDataSet(@NotNull ChartSettings chartSettings) {
    final String[] filterUsers = chartSettings.getParams().get("@filter.users");
    final Set<String> selectedUsers = new HashSet<String>();
    if(filterUsers!=null) {
      selectedUsers.addAll(Arrays.asList(filterUsers));
    }
    chartSettings.getParams().put("availableUsers", getAllCommitterNames(chartSettings.getParams().get("buildTypeId")));
    chartSettings.getParams().put("selectedUsers", new String[] {selectedUsers.toString()});
    List<BuildValue> entireCollection = myStorage.getDataSet(getKey(), (BuildChartSettings)chartSettings, null);
    if (!selectedUsers.isEmpty()) {
      return FilterUtil.filterCollection(new ArrayList<BuildValue>(entireCollection),
                                         createByUsersFilter(selectedUsers));
    } else {
      return entireCollection;
    }
  }

  private Filter<BuildValue> createByUsersFilter(final Set<String> selectedUsers) {
    return new Filter<BuildValue>() {
      public boolean accept(@NotNull BuildValue data) {
        return checkBuildContainsChangeAtLeastOneUserFrom(data, selectedUsers);
      }
    };
  }

  private boolean checkBuildContainsChangeAtLeastOneUserFrom(BuildValue data, Set<String> selectedUsers) {
    SBuild build = myServer.findBuildInstanceById(data.getBuildId());
    if (build != null) {
      List<SVcsModification> changes = build.getContainingChanges();
      for (SVcsModification change : changes) {
        if (selectedUsers.contains(change.getUserName())) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Unique statistics identifier.
   *
   * @return unique string key.
   */
  @NotNull
  public String getKey() {
    return "SampleStatistics";
  }

  public boolean hasData(final ChartSettings chartSettings) {
    return true;
  }

  private String[] getAllCommitterNames(String[] buildTypeId) {
    Set<String> availableUsers = new HashSet<String>();
    if (buildTypeId != null) {
      SBuildType buildType = myServer.getProjectManager().findBuildTypeByExternalId(buildTypeId[0]);
      if (buildType != null) {
        List<SVcsModification> allModifications = myServer.getVcsHistory().getAllModifications(buildType);
        availableUsers.addAll(getAllUserNames(allModifications));
      }
    }
    return availableUsers.toArray(new String[availableUsers.size()]);
  }

  /**
   * Includes each user who made modification in the given modification list
   *
   * @param allModifications
   * @return
   */
  private Set<String> getAllUserNames(List<SVcsModification> allModifications) {
    Set<String> names = new HashSet<String>();
    for (SVcsModification modification : allModifications) {
      if (modification.getUserName() != null) {
        names.add(modification.getUserName());
      }
    }

    return names;
  }

  @Nullable
  public String getValueFormat() {
    return "integer";
  }

  public String getSeriesColor(final String s) {
    return null;
  }

  /**
   * Publishes build statistics
   *
   * @param sBuild
   */
  public void buildFinished(SBuild sBuild) {
    myStorage
      .publishValue(getKey(), sBuild.getBuildId(), new BigDecimal(sBuild.getChanges(SelectPrevBuildPolicy.SINCE_LAST_BUILD, true).size()));
  }

  /**
   * Statistics description. Placed above the graph
   *
   * @param chartSettings current filter
   * @return
   */
  @NotNull
  public String getDescription(ChartSettings chartSettings) {
    String[] selectedUsers = chartSettings.getParams().get("@filter.users");
    if (selectedUsers == null || selectedUsers.length != 1) {
      return "Sample Statistics Description";
    } else {
      return "Sample Statistics for user " + selectedUsers[0];
    }
  }

  @NotNull
  public String getExtendedDescription(ChartSettings chartSettings) {
    return "Extended Description";
  }

  public String getSeriesGenericName() {
    return "Agent";
  }
}
