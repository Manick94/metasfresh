package de.metas.ui.web.order.sales.pricingConditions.view;

import java.util.List;

import org.adempiere.model.InterfaceWrapperHelper;
import org.adempiere.util.Services;

import com.google.common.collect.ImmutableList;

import de.metas.i18n.ITranslatableString;
import de.metas.interfaces.I_C_OrderLine;
import de.metas.order.IOrderDAO;
import de.metas.order.IOrderLineBL;
import de.metas.order.OrderLineId;
import de.metas.order.OrderLinePriceUpdateRequest;
import de.metas.pricing.conditions.PriceOverride;
import de.metas.pricing.conditions.PriceOverrideType;
import de.metas.pricing.conditions.PricingConditionsBreak;
import de.metas.process.RelatedProcessDescriptor;
import de.metas.ui.web.document.filter.DocumentFilter;
import de.metas.ui.web.document.filter.DocumentFilterDescriptorsProvider;
import de.metas.ui.web.document.filter.DocumentFiltersList;
import de.metas.ui.web.view.AbstractCustomView;
import de.metas.ui.web.view.IEditableView;
import de.metas.ui.web.view.ViewId;
import de.metas.ui.web.window.datatypes.DocumentId;
import de.metas.ui.web.window.datatypes.LookupValuesList;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;

/*
 * #%L
 * metasfresh-webui-api
 * %%
 * Copyright (C) 2018 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

public class PricingConditionsView extends AbstractCustomView<PricingConditionsRow> implements IEditableView
{
	public static PricingConditionsView cast(final Object viewObj)
	{
		return (PricingConditionsView)viewObj;
	}

	private final PricingConditionsRowData rowsData;
	private final List<RelatedProcessDescriptor> relatedProcessDescriptors;

	@Builder
	private PricingConditionsView(
			final ViewId viewId,
			final PricingConditionsRowData rowsData,
			@Singular final List<RelatedProcessDescriptor> relatedProcessDescriptors,
			@NonNull final DocumentFilterDescriptorsProvider filterDescriptors)
	{
		super(viewId, ITranslatableString.empty(), rowsData, filterDescriptors);
		this.rowsData = rowsData;
		this.relatedProcessDescriptors = ImmutableList.copyOf(relatedProcessDescriptors);
	}

	private PricingConditionsView(final PricingConditionsView from, final PricingConditionsRowData rowsData)
	{
		super(from.getViewId(), from.getDescription(), rowsData, from.getFilterDescriptors());
		this.rowsData = rowsData;
		this.relatedProcessDescriptors = from.relatedProcessDescriptors;
	}

	public OrderLineId getOrderLineId()
	{
		return rowsData.getOrderLineId();
	}

	@Override
	public String getTableNameOrNull(final DocumentId documentId)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<RelatedProcessDescriptor> getAdditionalRelatedProcessDescriptors()
	{
		return relatedProcessDescriptors;
	}

	@Override
	public LookupValuesList getFieldTypeahead(final RowEditingContext ctx, final String fieldName, final String query)
	{
		return getById(ctx.getRowId()).getFieldTypeahead(fieldName, query);
	}

	@Override
	public LookupValuesList getFieldDropdown(final RowEditingContext ctx, final String fieldName)
	{
		return getById(ctx.getRowId()).getFieldDropdown(fieldName);
	}

	public boolean hasEditableRow()
	{
		return rowsData.hasEditableRow();
	}

	public PricingConditionsRow getEditableRow()
	{
		return rowsData.getEditableRow();
	}

	public void patchEditableRow(@NonNull final PricingConditionsRowChangeRequest request)
	{
		rowsData.patchEditableRow(request);
	}

	@Override
	public List<DocumentFilter> getFilters()
	{
		return rowsData.getFilters().getFilters();
	}

	public PricingConditionsView filter(final DocumentFiltersList filters)
	{
		return new PricingConditionsView(this, rowsData.filter(filters));
	}

	public void updateSalesOrderLineIfPossible()
	{
		if (!hasEditableRow())
		{
			return;
		}

		final IOrderDAO ordersRepo = Services.get(IOrderDAO.class);
		final IOrderLineBL orderLineBL = Services.get(IOrderLineBL.class);

		final PricingConditionsRow editableRow = getEditableRow();
		final PricingConditionsBreak pricingConditionsBreak = editableRow.getPricingConditionsBreak();

		final I_C_OrderLine orderLine = ordersRepo.getOrderLineById(getOrderLineId());
		orderLine.setIsTempPricingConditions(pricingConditionsBreak.isTemporaryPricingConditionsBreak());

		if (pricingConditionsBreak.isTemporaryPricingConditionsBreak())
		{
			orderLine.setM_DiscountSchemaBreak_ID(-1);

			final PriceOverride price = pricingConditionsBreak.getPriceOverride();
			final PriceOverrideType type = price.getType();
			if (type == PriceOverrideType.NONE)
			{
				//
			}
			else if (type == PriceOverrideType.BASE_PRICING_SYSTEM)
			{
				orderLine.setIsManualPrice(true);
				orderLine.setPriceEntered(editableRow.getPriceNet());
				orderLine.setBase_PricingSystem_ID(price.getBasePricingSystemId());

			}
			else if (type == PriceOverrideType.FIXED_PRICE)
			{
				orderLine.setIsManualPrice(true);
				orderLine.setPriceEntered(price.getFixedPrice());
			}

			orderLine.setIsManualDiscount(true);
			orderLine.setDiscount(pricingConditionsBreak.getDiscount().getValueAsBigDecimal());
			orderLine.setC_PaymentTerm_Override_ID(pricingConditionsBreak.getPaymentTermId());
		}
		else
		{
			orderLine.setIsManualDiscount(false);
			orderLine.setIsManualPrice(false);
			orderLineBL.updatePrices(OrderLinePriceUpdateRequest.ofOrderLine(orderLine));
		}

		orderLineBL.updateLineNetAmt(orderLine);
		orderLineBL.setTaxAmtInfo(orderLine);

		InterfaceWrapperHelper.save(orderLine);
	}
}
