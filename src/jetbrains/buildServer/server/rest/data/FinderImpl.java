/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data;

import com.intellij.openapi.diagnostic.Logger;
import java.util.*;
import java.util.concurrent.TimeUnit;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.util.ItemProcessor;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 06/06/2016
 */
public class FinderImpl<ITEM> implements Finder<ITEM> {
  private static final Logger LOG = Logger.getInstance(AbstractFinder.class.getName());

  public static final String DIMENSION_ID = "id";
  public static final String DIMENSION_LOOKUP_LIMIT = "lookupLimit";

  public static final String LOGIC_OP_OR = "or";
  public static final String LOGIC_OP_AND = "and";
  public static final String LOGIC_OP_NOT = "not";
  public static final String DIMENSION_ITEM = "item";
  public static final String DIMENSION_UNIQUE = "unique";
  protected static final String OPTIONS_REPORT_ERROR_ON_NOTHING_FOUND = "$reportErrorOnNothingFound";

  //todo: add set-filtering (filter by collection of items in prefiltering and in filter), e.g. see handling of ProjectFinder.DIMENSION_PROJECT
  private FinderDataBinding<ITEM> myDataBinding;

  public FinderImpl(@NotNull final FinderDataBinding<ITEM> dataBinding) {
    myDataBinding = dataBinding;
  }

  //todo: rework to remove the constructor and add final to  myDataBinding
  public FinderImpl() {
  }

  public void setDataBinding(@NotNull final FinderDataBinding<ITEM> dataBinding) {
    if (myDataBinding != null) throw new OperationException("Logic error: cannot re-initialize dataBinding in FinderImpl");
    myDataBinding = dataBinding;
  }

  @NotNull
  @Override
  public String getCanonicalLocator(@NotNull final ITEM item) {
    return myDataBinding.getItemLocator(item);
  }

  @Override
  @NotNull
  public ITEM getItem(@Nullable final String locatorText) {
    return getItem(locatorText, null);
  }

  /**
   * @throws NotFoundException if there is locator sub-dimension which references a single entry which cannot be found (might need to return empty collection for the case as well)
   * @returns the items found by locatorText or empty collection if the locator does ot correspond to any item
   */
  @Override
  @NotNull
  public PagedSearchResult<ITEM> getItems(@Nullable final String locatorText) {
    return getItemsByLocator(getLocatorOrNull(locatorText), true);
  }

  @NotNull
  @Override
  public ItemFilter<ITEM> getFilter(@NotNull final String locatorText) {
    final Locator locator = createLocator(locatorText, null);
    final ItemFilter<ITEM> result;
    try {
      result = getFilterWithLogicOpsSupport(locator, myDataBinding.getLocatorDataBinding(locator));
    } catch (LocatorProcessException|BadRequestException e){
      if (!locator.isHelpRequested()){
        throw e;
      }
      throw new BadRequestException(e.getMessage() +
                                    "\nLocator details: " + locator.getLocatorDescription(locator.helpOptions().getSingleDimensionValueAsStrictBoolean("hidden", false)), e);
    }
    locator.checkLocatorFullyProcessed();
    return result;
  }


  /**
   * @throws NotFoundException if there is locator sub-dimension which  references a single entry which cannot be found (might need to return empty collection for the case as well)
   * @returns the items found by locatorText or empty collection if the locator does ot correspond to any item
   */
  @NotNull
  public PagedSearchResult<ITEM> getItems(@Nullable final String locatorText, @Nullable final Locator locatorDefaults) {
    return getItemsByLocator(getLocatorOrNull(locatorText, locatorDefaults), true);
  }


  @NotNull
  protected Locator createLocator(@Nullable final String locatorText, @Nullable final Locator locatorDefaults) {
    List<String> knownDimensions = new ArrayList<>(Arrays.asList(myDataBinding.getKnownDimensions()));
    knownDimensions.add(PagerData.START);
    knownDimensions.add(PagerData.COUNT);
    knownDimensions.add(DIMENSION_LOOKUP_LIMIT);
    knownDimensions.add(OPTIONS_REPORT_ERROR_ON_NOTHING_FOUND);
    final Locator result = Locator.createLocator(locatorText, locatorDefaults, knownDimensions.toArray(new String[knownDimensions.size()]));
    result.addIgnoreUnusedDimensions(PagerData.COUNT);
    result.addIgnoreUnusedDimensions(OPTIONS_REPORT_ERROR_ON_NOTHING_FOUND);
    result.addHiddenDimensions(LOGIC_OP_OR, LOGIC_OP_AND, LOGIC_OP_NOT, AbstractFinder.DIMENSION_ITEM);  //experimental
    result.addHiddenDimensions(AbstractFinder.DIMENSION_UNIQUE);  //experimental, should actually depend on FinderDataBinding.getContainerSet returning not null
    result.addHiddenDimensions(OPTIONS_REPORT_ERROR_ON_NOTHING_FOUND); //experimental
    for (String hiddenDimension : myDataBinding.getHiddenDimensions()) {
      result.addHiddenDimensions(hiddenDimension);
    }
    Locator.DescriptionProvider descriptionProvider = myDataBinding.getLocatorDescriptionProvider();
    if (descriptionProvider != null) {
      result.setDescriptionProvider(descriptionProvider);
    }
    return result;
  }

  @Nullable
  @Contract("null -> null; !null -> !null")
  private Locator getLocatorOrNull(@Nullable final String locatorText) {
    return locatorText != null ? createLocator(locatorText, null) : null;
  }

  @Nullable
  @Contract("null, null -> null; !null, _ -> !null; _, !null -> !null")
  private Locator getLocatorOrNull(@Nullable final String locatorText, @Nullable final Locator locatorDefaults) {
    return (locatorText == null && locatorDefaults == null) ? null : createLocator(locatorText, locatorDefaults);
  }

  @NotNull
  private ITEM getItem(@Nullable final String locatorText, @Nullable final Locator locatorDefaults) {
    if (StringUtil.isEmpty(locatorText)) {
      throw new BadRequestException("Empty locator is not supported.");
    }
    final Locator locator = createLocator(locatorText, locatorDefaults);

    if (!locator.isSingleValue()) {
      locator.setDimension(PagerData.COUNT, "1"); //get only the first one that matches
      locator.addHiddenDimensions(PagerData.COUNT);
    }
    final PagedSearchResult<ITEM> items = getItemsByLocator(locator, false);
    final int entriesSize = items.myEntries.size();
    if (entriesSize == 0) {
      if (!items.myLookupLimitReached) {
        throw new NotFoundException("Nothing is found by locator '" + locator.getStringRepresentation() + "'.");
      }
      LOG.debug("Returning \"Not Found\" response because of reaching lookupLimit. Last processed item: " + LogUtil.describe(items.getLastProcessedItem()));
      throw new NotFoundException("Nothing is found by locator '" + locator.getStringRepresentation() + "' while processing first " +
                                  items.myLookupLimit + " items. Set " + DIMENSION_LOOKUP_LIMIT + " dimension to larger value to process more items.");
    }
    if (entriesSize != 1) {
      throw new OperationException("Found + " + entriesSize + " items for locator '" + locator.getStringRepresentation() + "' while a single item is expected.");
    }
    return items.myEntries.get(0);
  }

  @NotNull
  private PagedSearchResult<ITEM> getItemsByLocator(@Nullable final Locator originalLocator, final boolean multipleItemsQuery) {
    Locator locator;
    if (originalLocator == null) {
      //go on with empty locator
      locator = createLocator(null, Locator.createEmptyLocator());
    } else {
      locator = originalLocator;

      ITEM singleItem = null;
      try {
        singleItem = myDataBinding.findSingleItem(locator);
      } catch (NotFoundException e) {
        if (multipleItemsQuery && !isReportErrorOnNothingFound(locator)) {
          //consider adding comment/warning messages to PagedSearchResult, return it as a header in the response
          //returning empty collection for multiple items query
          return new PagedSearchResult<ITEM>(Collections.<ITEM>emptyList(), null, null);
        }
        throw e;
      } catch (LocatorProcessException|BadRequestException e){
        if (!locator.isHelpRequested()){
          throw e;
        }
        throw new BadRequestException(e.getMessage() +
                                      "\nLocator details: " + locator.getLocatorDescription(locator.helpOptions().getSingleDimensionValueAsStrictBoolean("hidden", false)), e);
      }
      if (singleItem != null) {
        final Set<String> singleItemUsedDimensions = locator.getUsedDimensions();
        // ignore start:0 dimension
        final Long startDimension = locator.getSingleDimensionValueAsLong(PagerData.START);
        if (startDimension == null || startDimension != 0) {
          locator.markUnused(PagerData.START);
        }

        ItemFilter<ITEM> filter = null;
        try {
          filter = getDataBindingWithLogicOpsSupport(locator, myDataBinding).getFilter();
        } catch (NotFoundException e) {
          throw new NotFoundException("Invalid filter for found single item, try omitting extra dimensions: " + e.getMessage(), e);
        } catch (BadRequestException e) {
          throw new BadRequestException("Invalid filter for found single item, try omitting extra dimensions: " + e.getMessage(), e);
        } catch (Exception e) {
          throw new BadRequestException("Invalid filter for found single item, try omitting extra dimensions: " + e.toString(), e);
        }
        locator.getSingleDimensionValue(DIMENSION_UNIQUE); //mark as used as it has no influence on single item
        locator.checkLocatorFullyProcessed(); //checking before invoking filter to report any unused dimensions before possible error reporting in filter
        if (!filter.isIncluded(singleItem)) {
          final String message = "Found single item by " + StringUtil.pluralize("dimension", singleItemUsedDimensions.size()) + " " + singleItemUsedDimensions +
                                 ", but that was filtered out using the entire locator '" + locator + "'";
          if (multipleItemsQuery && !isReportErrorOnNothingFound(locator)) {
            LOG.debug(message);
            return new PagedSearchResult<ITEM>(Collections.<ITEM>emptyList(), null, null);
          } else {
            throw new NotFoundException(message);
          }
        }

        return new PagedSearchResult<ITEM>(Collections.singletonList(singleItem), null, null);
      }
      locator.markAllUnused(); // nothing found - no dimensions should be marked as used then
    }

    FinderDataBinding.LocatorDataBinding<ITEM> locatorDataBinding = getDataBindingWithLogicOpsSupport(locator, myDataBinding);
    FinderDataBinding.ItemHolder<ITEM> unfilteredItems = locatorDataBinding.getPrefilteredItems();
    Set<ITEM> containerSet = myDataBinding.createContainerSet();
    if (containerSet != null) {
      boolean deduplicate = locator.getSingleDimensionValueAsStrictBoolean(DIMENSION_UNIQUE, locator.isAnyPresent(DIMENSION_ITEM));
      if (deduplicate) {
        unfilteredItems = new FinderDataBinding.DeduplicatingItemHolder<>(unfilteredItems, containerSet);
      }
    }

    final Long start = locator.getSingleDimensionValueAsLong(PagerData.START);
    final Long countFromFilter = locator.getSingleDimensionValueAsLong(PagerData.COUNT, myDataBinding.getDefaultPageItemsCount());
    Long lookupLimit = locator.getSingleDimensionValueAsLong(DIMENSION_LOOKUP_LIMIT, myDataBinding.getDefaultLookupLimit());

    if (countFromFilter != null && lookupLimit != null && locator.getSingleDimensionValue(DIMENSION_LOOKUP_LIMIT) == null && lookupLimit < countFromFilter) {
      // if count of items is set, but lookupLimit is not, process at least as many items as count
      lookupLimit = countFromFilter;
    }

    final PagingItemFilter<ITEM> pagingFilter = new PagingItemFilter<ITEM>(locatorDataBinding.getFilter(),
                                                                           start, countFromFilter == null ? null : countFromFilter.intValue(),
                                                                           lookupLimit);

    locator.checkLocatorFullyProcessed();
    return getItems(pagingFilter, unfilteredItems, locator);
  }

  private static boolean isReportErrorOnNothingFound(final @NotNull Locator locator) {
    return locator.getSingleDimensionValueAsStrictBoolean(OPTIONS_REPORT_ERROR_ON_NOTHING_FOUND, false) || locator.isHelpRequested();
  }

  @NotNull
  private PagedSearchResult<ITEM> getItems(final @NotNull PagingItemFilter<ITEM> filter,
                                           final @NotNull FinderDataBinding.ItemHolder<ITEM> unfilteredItems,
                                           @NotNull final Locator locator) {
    final long startTime = System.nanoTime();
    final FilterItemProcessor<ITEM> filterItemProcessor = new FilterItemProcessor<ITEM>(filter);
    unfilteredItems.process(filterItemProcessor);
    final ArrayList<ITEM> result = filterItemProcessor.getResult();
    final long finishTime = System.nanoTime();
    final long totalItemsProcessed = filterItemProcessor.getTotalItemsProcessed();
    final long processingTimeMs = TimeUnit.MILLISECONDS.convert(finishTime - startTime, TimeUnit.NANOSECONDS);
    if (totalItemsProcessed >= TeamCityProperties.getLong("rest.finder.processedItemsLogLimit", 1)) {
      final String lookupLimitMessage =
        filter.isLookupLimitReached() ? " (lookupLimit of " + filter.getLookupLimit() + " reached). Last processed item: " + LogUtil.describe(filter.getLastProcessedItem()) : "";
      LOG.debug("While processing locator '" + locator + "', " + result.size() + " items were matched by the filter from " + totalItemsProcessed + " processed in total" +
                lookupLimitMessage + ", took " + processingTimeMs + " ms"); //todo make AbstractFilter loggable and add logging here
    }
    if (totalItemsProcessed > TeamCityProperties.getLong("rest.finder.processedItemsWarnLimit", 10000) ||
        processingTimeMs > TeamCityProperties.getLong("rest.finder.timeWarnLimit", 10000)) {
      LOG.info("Server performance can be affected by REST request with locator '" + locator + "': " +
               totalItemsProcessed + " items were processed and " + result.size() + " items were returned, took " + processingTimeMs + " ms");
    }
    if (result.isEmpty() && isReportErrorOnNothingFound(locator)){
      throw new NotFoundException("Nothing is found by locator '" + locator.getStringRepresentation() + "'.");
    }
    return new PagedSearchResult<ITEM>(result, filter.getStart(), filter.getCount(), totalItemsProcessed,
                                       filter.getLookupLimit(), filter.isLookupLimitReached(), filter.getLastProcessedItem());
  }

  @NotNull
  private ItemFilter<ITEM> getFilterWithLogicOpsSupport(@NotNull final Locator locator, @NotNull final FinderDataBinding.LocatorDataBinding<ITEM> dataBinding) {
    AndFilterBuilder<ITEM> result = new AndFilterBuilder<ITEM>();
    result.add(dataBinding.getFilter());

    final String orDimension = locator.getSingleDimensionValue(LOGIC_OP_OR); //consider adding for multiple support here, use getItemsAnd()
    if (orDimension != null) {
      result.add(getFilterOr(getListOfSubLocators(orDimension)));
    }

    final String andDimension = locator.getSingleDimensionValue(LOGIC_OP_AND);  //consider adding for multiple support here, use getItemsAnd()
    if (andDimension != null) {
      result.add(getFilter(andDimension));
    }

    final String notDimension = locator.getSingleDimensionValue(LOGIC_OP_NOT);  //consider adding for multiple support here, use getItemsAnd()
    if (notDimension != null) {
      ItemFilter<ITEM> notFilter = getFilter(notDimension);
      return new ItemFilter<ITEM>() {
        @Override
        public boolean shouldStop(@NotNull final ITEM item) {
          return false;
        }

        @Override
        public boolean isIncluded(@NotNull final ITEM item) {
          return !notFilter.isIncluded(item);
        }
      };
    }

    return result.build();
  }

  /**
   * @param locator
   * @param dataBinding
   * @return null, if no logic ops are found within locator and it should be processed as usual, otherwise, result items which require no additional processing.
   */
  @NotNull
  private FinderDataBinding.LocatorDataBinding<ITEM> getDataBindingWithLogicOpsSupport(@NotNull final Locator locator,
                                                                                       @NotNull final FinderDataBinding<ITEM> originalDataBinding) {
    return new FinderDataBinding.LocatorDataBinding<ITEM>() {
      private FinderDataBinding.LocatorDataBinding<ITEM> myLocatorDataBinding;

      @NotNull
      @Override
      public FinderDataBinding.ItemHolder<ITEM> getPrefilteredItems() {
        final List<String> itemDimension = locator.getDimensionValue(DIMENSION_ITEM);
        if (!itemDimension.isEmpty()) {
          return getItemsOr(itemDimension);
        }

        if (myLocatorDataBinding == null) {
          myLocatorDataBinding = originalDataBinding.getLocatorDataBinding(locator);
        }
        return myLocatorDataBinding.getPrefilteredItems();
      }

      @NotNull
      @Override
      public ItemFilter<ITEM> getFilter() {
        if (myLocatorDataBinding == null) {
          myLocatorDataBinding = originalDataBinding.getLocatorDataBinding(locator);
        }
        return getFilterWithLogicOpsSupport(locator, myLocatorDataBinding);
      }
    };
  }

  @NotNull
  private List<String> getListOfSubLocators(@NotNull final String locatorText) {
    Locator locator = new Locator(locatorText);
    if (locator.isSingleValue()) {
      return Collections.singletonList(locator.getStringRepresentation());
    }
    ArrayList<String> result = new ArrayList<>();
    for (String dimensionName : locator.getDefinedDimensions()) {
      for (String value : locator.getDimensionValue(dimensionName)) {
        result.add(Locator.getStringLocator(dimensionName, value));
      }
    }
    return result;
  }

  @NotNull
  private ItemFilter<ITEM> getFilterOr(@NotNull final List<String> itemsDimension) {
    OrFilterBuilder<ITEM> result = new OrFilterBuilder<ITEM>();
    for (String itemLocator : itemsDimension) {
      result.add(getFilter(itemLocator));
    }
    return result.build();
  }

  private static class OrFilterBuilder<T> {
    @NotNull private final List<ItemFilter<T>> myCheckers;

    OrFilterBuilder() {
      myCheckers = new ArrayList<ItemFilter<T>>();
    }

    public OrFilterBuilder<T> add(ItemFilter<T> checker) {
      myCheckers.add(checker);
      return this;
    }

    public ItemFilter<T> build() {
      return new ItemFilter<T>() {
        @Override
        public boolean shouldStop(@NotNull final T item) {
          for (ItemFilter<T> checker : myCheckers) {
            if (!checker.shouldStop(item)) return false;
          }
          return true;
        }

        @Override
        public boolean isIncluded(@NotNull final T item) {
          for (ItemFilter<T> checker : myCheckers) {
            if (checker.isIncluded(item)) {
              return true;
            }
          }
          return false;
        }
      };
    }
  }

  //see also MultiCheckerFilter
  private static class AndFilterBuilder<T> {
    @NotNull private final List<ItemFilter<T>> myCheckers;

    AndFilterBuilder() {
      myCheckers = new ArrayList<ItemFilter<T>>();
    }

    public AndFilterBuilder<T> add(ItemFilter<T> checker) {
      myCheckers.add(checker);
      return this;
    }

    public ItemFilter<T> build() {
      return new ItemFilter<T>() {
        @Override
        public boolean shouldStop(@NotNull final T item) {
          for (ItemFilter<T> checker : myCheckers) {
            if (checker.shouldStop(item)) {
              return true;
            }
          }
          return false;
        }

        @Override
        public boolean isIncluded(@NotNull final T item) {
          for (ItemFilter<T> checker : myCheckers) {
            if (!checker.isIncluded(item)) {
              return false;
            }
          }
          return true;
        }
      };
    }
  }

  @NotNull
  private FinderDataBinding.ItemHolder<ITEM> getItemsOr(@NotNull final List<String> itemsDimension) {
    return new FinderDataBinding.ItemHolder<ITEM>() {
      @Override
      public void process(@NotNull final ItemProcessor<ITEM> processor) {
        for (String itemLocator : itemsDimension) {
          FinderDataBinding.getItemHolder(getItems(itemLocator).myEntries).process(processor);  //todo: rework APIs to add itemHolders instead of serialized collection
        }
      }
    };
  }

  /*
  @NotNull
  private FinderDataBinding.LocatorDataBinding<ITEM> getItemsAnd(@NotNull final List<String> itemsDimension) {
    if (itemsDimension.size() == 0) {
      throw new BadRequestException("Unsupported empty locator for 'and' processing");
    }
    if (itemsDimension.size() == 1) {
      return new FinderDataBinding.LocatorDataBinding<ITEM>() {
        @NotNull
        @Override
        public FinderDataBinding.ItemHolder<ITEM> getPrefilteredItems() {
          return FinderDataBinding.getItemHolder(getItems(itemsDimension.get(0)).myEntries);
        }

        @NotNull
        @Override
        public ItemFilter<ITEM> getFilter() {
          return DO_NOTHING_FILTER;
        }
      };
    }
    Iterator<String> it = itemsDimension.iterator();
    List<ITEM> firstSet = getItems(it.next()).myEntries;
    MultiCheckerFilter<ITEM> filter = new MultiCheckerFilter<ITEM>();
    while (it.hasNext()) {
      filter.add(getFilter(it.next()));
    }
    return new FinderDataBinding.LocatorDataBinding<ITEM>() {
      @NotNull
      @Override
      public FinderDataBinding.ItemHolder<ITEM> getPrefilteredItems() {
        return FinderDataBinding.getItemHolder(firstSet);
      }

      @NotNull
      @Override
      public ItemFilter<ITEM> getFilter() {
        return new ItemFilter<ITEM>() {
          @Override
          public boolean shouldStop(@NotNull final ITEM item) {
            return false;
          }

          @Override
          public boolean isIncluded(@NotNull final ITEM item) {
            return filter.isIncluded(item);
          }
        };
      }
    };
  }

  private final ItemFilter<ITEM> DO_NOTHING_FILTER = new ItemFilter<ITEM>() {
    @Override
    public boolean shouldStop(@NotNull final ITEM item) {
      return false;
    }

    @Override
    public boolean isIncluded(@NotNull final ITEM item) {
      return true;
    }
  };
  */
}