package org.yes.cart.web.page.component.breadcrumbs;

import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.util.string.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yes.cart.domain.entity.Category;
import org.yes.cart.service.domain.CategoryService;
import org.yes.cart.web.support.constants.WebParametersKeys;
import org.yes.cart.web.support.util.HttpUtil;
import org.yes.cart.web.util.WicketUtil;

import java.util.*;

/**
 * Bread crumbs builder produce category and
 * attributive filtered navigation breadcrumbs based on
 * web query string and context.
 * <p/>
 * <p/>
 * User: Igor Azarny iazarny@yahoo.com
 * Date: 2011-May-17
 * Time: 9:50:51 AM
 */
public class BreadCrumbsBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(BreadCrumbsBuilder.class);

    private final CrumbNamePrefixProvider namePrefixProvider;
    private final List<Long> shopCategoryIds;
    private final long categoryId;
    private final PageParameters pageParameters;
    private final List<String> allowedAttributeNames;
    private final CategoryService categoryService;

    /**
     * Bread crumbs builder constructor.
     *
     * @param categoryId            current category id
     * @param pageParameters        current query string
     * @param allowedAttributeNames allowed attribute names for filtering including price, brand, search...
     * @param shopCategoryIds       all categoryIds, that belong to shop
     * @param namePrefixProvider    name prifix provider for price, brand, search..
     * @param categoryService       category service
     */
    public BreadCrumbsBuilder(
            final long categoryId,
            final PageParameters pageParameters,
            final List<String> allowedAttributeNames,
            final List<Long> shopCategoryIds,
            final CrumbNamePrefixProvider namePrefixProvider,
            final CategoryService categoryService) {

        this.namePrefixProvider = namePrefixProvider;
        this.shopCategoryIds = shopCategoryIds;
        this.categoryId = categoryId;
        this.pageParameters = pageParameters;
        this.allowedAttributeNames = allowedAttributeNames;
        this.categoryService = categoryService;
    }


    /**
     * We have 2 kinds of breadcrumbs:
     * 1. category path, for example electronics -> phones -> ip phones
     * 2. attributive filters, for example ip phones [price range, brands, weight, ect]
     * @return list of crumbs
     */
    public List<Crumb> getBreadCrumbs() {
        final List<Crumb> crumbs = new ArrayList<Crumb>();
        crumbs.addAll(getCategoriesCrumbs(categoryId));
        crumbs.addAll(getFilteredNavigationCrumbs(allowedAttributeNames));
        return crumbs;
    }

    private List<Crumb> getFilteredNavigationCrumbs(final List<String> allowedAttributeNames) {
        final List<Crumb> navigationCrumbs = new ArrayList<Crumb>();
        fillAttributes(navigationCrumbs, allowedAttributeNames);
        return navigationCrumbs;
    }

    private List<Crumb> getCategoriesCrumbs(final long categoryId) {
        final List<Crumb> categoriesCrumbs = new ArrayList<Crumb>();
        if (categoryId > 0) {
            fillCategories(categoriesCrumbs, categoryId);
            Collections.reverse(categoriesCrumbs);
        }
        return categoriesCrumbs;
    }


    /**
     * Recursive function to reverse build the breadcrumbs by categories, starting from currently selected one.
     *
     * @param categoriesCrumbs the crumbs list
     * @param categoryId       the current category id
     */
    private void fillCategories(final List<Crumb> categoriesCrumbs, final long categoryId) {
        final Category category = categoryService.getById(categoryId);
        if (categoryId != category.getParentId()) {
            categoriesCrumbs.add(
                   new Crumb(category.getName(),
                   getCategoryLinkParameters(categoryId),
                   getRemoveCategoryLinkParameters(category))
            );

            if (LOG.isDebugEnabled()) {
                LOG.debug("Adding breadcrumb for category: [" + categoryId + "] " + category.getName());
            }
            fillCategories(categoriesCrumbs, category.getParentId());
        }
    }

    /**
     * Get {@link PageParameters}, that point to given category.
     * @param categoryId given category id
     * @return  page parameters for link
     */
    public PageParameters getCategoryLinkParameters(final long categoryId) {
        return new PageParameters().add(WebParametersKeys.CATEGORY_ID, categoryId);
    }

    /**
     * Get {@link PageParameters}, that point to parent, if any, of given category.
     * @param category given category
     * @return page parameter for point to parent.
     */
    private PageParameters getRemoveCategoryLinkParameters(final Category category) {
        if (shopCategoryIds.contains(category.getParentId())) {
            return getCategoryLinkParameters(category.getParentId());
        }
        return new PageParameters();
    }

    private void fillAttributes(
            final List<Crumb> navigationCrumbs,
            final List<String> allowedAttributeNames) {

        //This is attributive only filtered navigation from request
        final PageParameters attributesOnly = WicketUtil.getRetainedRequestParameters(
                pageParameters,
                allowedAttributeNames);

        //Base hold category path from begining and accumulate all attributive navigation
        final PageParameters base = WicketUtil.getFilteredRequestParameters(
                pageParameters,
                allowedAttributeNames);

        //If we are on display product page, we have to remove for filtering  as well as sku
        base.remove(WebParametersKeys.PRODUCT_ID);
        base.remove(WebParametersKeys.SKU_ID);

        for (PageParameters.NamedPair namedPair : attributesOnly.getAllNamed()) {
            navigationCrumbs.add(createFilteredNavigationCrumb(base, namedPair.getKey(), namedPair.getValue()));
        }
    }

    /**
     * Create filtered navigation crubm with two links:
     * <p/>
     * First - current position, that include the whole path before current.
     * example category/17/subcategory/156/proce/100-200/currentkey/currentvalue
     * <p/>
     * Second - the whole current path without current
     * example category/17/subcategory/156/proce/100-200/currentkey/currentvalue/nextkey/nextvalue
     * ^^^^^^^^^^^^^^^^^^^^^^^ this will be removed,
     * so uri will be
     * example category/17/subcategory/156/proce/100-200/nextkey/nextvalue
     *
     * @param base  initial parameter map, usually category and sub category navigation
     * @param key   current key
     * @param value current value
     * @return {@link Crumb}
     */
    private Crumb createFilteredNavigationCrumb(
            final PageParameters base,
            final String key,
            final String value) {

        final PageParameters withoutCurrent = WicketUtil.getFilteredRequestParameters(
                pageParameters,
                key,
                value
        );

        final String linkName = namePrefixProvider.getLinkNamePrefix(key)
                + "::"
                + namePrefixProvider.getLinkName(key, value);
        base.add(key, value);
        return new Crumb(linkName, new PageParameters(base), withoutCurrent);
    }

}