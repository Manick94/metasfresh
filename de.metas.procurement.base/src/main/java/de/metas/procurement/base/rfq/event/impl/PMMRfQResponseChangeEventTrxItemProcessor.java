package de.metas.procurement.base.rfq.event.impl;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.concurrent.atomic.AtomicInteger;

import org.adempiere.ad.trx.processor.spi.TrxItemProcessorAdapter;
import org.adempiere.model.InterfaceWrapperHelper;
import org.adempiere.util.Check;
import org.adempiere.util.Services;
import org.slf4j.Logger;

import de.metas.logging.LogManager;
import de.metas.procurement.base.IPMM_RfQ_BL;
import de.metas.procurement.base.IPMM_RfQ_DAO;
import de.metas.procurement.base.model.I_PMM_RfQResponse_ChangeEvent;
import de.metas.procurement.base.model.X_PMM_RfQResponse_ChangeEvent;
import de.metas.rfq.model.I_C_RfQResponse;
import de.metas.rfq.model.I_C_RfQResponseLine;
import de.metas.rfq.model.I_C_RfQResponseLineQty;

/*
 * #%L
 * de.metas.procurement.base
 * %%
 * Copyright (C) 2016 metas GmbH
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

class PMMRfQResponseChangeEventTrxItemProcessor extends TrxItemProcessorAdapter<I_PMM_RfQResponse_ChangeEvent, Void>
{
	// service
	private static final transient Logger logger = LogManager.getLogger(PMMRfQResponseChangeEventTrxItemProcessor.class);
	private final transient IPMM_RfQ_DAO pmmRfqDAO = Services.get(IPMM_RfQ_DAO.class);
	private final transient IPMM_RfQ_BL pmmRfqBL = Services.get(IPMM_RfQ_BL.class);

	//
	// Status
	private final AtomicInteger countProcessed = new AtomicInteger(0);

	@Override
	public void process(final I_PMM_RfQResponse_ChangeEvent event) throws Exception
	{
		final String type = event.getType();

		try
		{
			if (X_PMM_RfQResponse_ChangeEvent.TYPE_Price.equals(type))
			{
				process_PriceChangeEvent(event);
			}
			else if (X_PMM_RfQResponse_ChangeEvent.TYPE_Quantity.equals(type))
			{
				process_QuantityChangeEvent(event);
			}
			else
			{
				throw new RuntimeException("Unknown type: " + type);
			}
		}
		catch (final Exception e)
		{
			logger.warn("Failed processing {}", event, e);
			markError(event, e.getLocalizedMessage());
		}
	}

	private void markProcessed(final I_PMM_RfQResponse_ChangeEvent event)
	{
		event.setProcessed(true);
		event.setIsError(false);
		event.setErrorMsg(null);
		InterfaceWrapperHelper.save(event);

		countProcessed.incrementAndGet();
	}

	private void markError(final I_PMM_RfQResponse_ChangeEvent event, final String errorMsg)
	{
		event.setProcessed(true);
		event.setIsError(true);
		event.setErrorMsg(errorMsg);
		InterfaceWrapperHelper.save(event);
	}

	private void assertRfqResponseNotClosed(final I_PMM_RfQResponse_ChangeEvent event)
	{
		final I_C_RfQResponseLine rfqResponseLine = event.getC_RfQResponseLine();
		Check.assumeNotNull(rfqResponseLine, "rfqResponseLine not null");

		final I_C_RfQResponse rfqResponse = rfqResponseLine.getC_RfQResponse();
		if (pmmRfqBL.isClosed(rfqResponse))
		{
			throw new RuntimeException("@C_RfQResponse_ID@ " + rfqResponse + " already closed");
		}
	}

	private void process_PriceChangeEvent(final I_PMM_RfQResponse_ChangeEvent event)
	{
		assertRfqResponseNotClosed(event);
		final I_C_RfQResponseLine rfqResponseLine = event.getC_RfQResponseLine();

		final BigDecimal priceOld = rfqResponseLine.getPrice();
		rfqResponseLine.setPrice(event.getPrice());
		InterfaceWrapperHelper.save(rfqResponseLine);

		//
		// Update event
		event.setPrice_Old(priceOld);
		markProcessed(event);
	}

	private void process_QuantityChangeEvent(final I_PMM_RfQResponse_ChangeEvent event)
	{
		assertRfqResponseNotClosed(event);
		final I_C_RfQResponseLine rfqResponseLine = event.getC_RfQResponseLine();

		final Timestamp date = event.getDatePromised();
		I_C_RfQResponseLineQty rfqResponseLineQty = pmmRfqDAO.retrieveResponseLineQty(rfqResponseLine, date);
		if (rfqResponseLineQty == null)
		{
			rfqResponseLineQty = InterfaceWrapperHelper.newInstance(I_C_RfQResponseLineQty.class, event);
			rfqResponseLineQty.setAD_Org_ID(rfqResponseLine.getAD_Org_ID());
			rfqResponseLineQty.setC_RfQResponseLine(rfqResponseLine);
			rfqResponseLineQty.setC_RfQLine(rfqResponseLine.getC_RfQLine());
			rfqResponseLineQty.setC_RfQLineQty(null);
			rfqResponseLineQty.setDatePromised(date);
		}

		final BigDecimal qtyOld = rfqResponseLineQty.getQtyPromised();
		rfqResponseLineQty.setQtyPromised(event.getQty());
		InterfaceWrapperHelper.save(rfqResponseLineQty);

		//
		// Update event
		event.setQty_Old(qtyOld);
		event.setC_RfQResponseLineQty(rfqResponseLineQty);
		markProcessed(event);
	}

	public String getProcessSummary()
	{
		return "@Processed@ #" + countProcessed.get();
	}
}
