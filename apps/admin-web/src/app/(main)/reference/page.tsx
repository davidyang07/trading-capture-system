"use client";

import { useState } from "react";
import { Column } from "primereact/column";
import { Dropdown } from "primereact/dropdown";
import { TabView, TabPanel } from "primereact/tabview";
import Table from "@/components/Table/Table";
import { PageHeader } from "@/components/PageHeader";
import { StatusBadge } from "@/components/StatusBadge";
import { AsyncBoundary } from "@/components/feedback/States";
import { useQuery } from "@/hooks/useQuery";
import {
  listCurrencies,
  listExchanges,
  searchInstruments,
} from "@/api/reference";
import { humanizeEnum } from "@/lib/format";
import type {
  Currency,
  Exchange,
  InstrumentSearchParams,
  InstrumentSummary,
} from "@/types/api";

const INSTRUMENT_TYPE_OPTIONS = ["EQUITY", "BOND", "ETF", "FUND"].map((v) => ({
  label: humanizeEnum(v),
  value: v,
}));

function InstrumentsTab() {
  const [filters, setFilters] = useState<InstrumentSearchParams>({
    page: 0,
    size: 10,
    sortBy: "NAME",
    sortDir: "ASC",
  });
  const query = useQuery(
    (signal) => searchInstruments(filters, signal),
    [filters]
  );
  return (
    <>
      <div className="filter-bar" role="search">
        <label className="filter-item">
          <span>Type</span>
          <Dropdown
            value={filters.instrumentType}
            options={INSTRUMENT_TYPE_OPTIONS}
            optionLabel="label"
            optionValue="value"
            showClear
            placeholder="Any"
            ariaLabel="Type"
            pt={{ select: { "aria-label": "Type" } }}
            onChange={(e) =>
              setFilters((p) => ({ ...p, page: 0, instrumentType: e.value }))
            }
          />
        </label>
      </div>
      <AsyncBoundary
        loading={query.loading}
        error={query.error}
        data={query.data}
        onRetry={query.refetch}
        isEmpty={(p) => p.content.length === 0}
        emptyMessage="No instruments found."
      >
        {(p) => (
          <Table
            dataList={p.content}
            totalNum={p.totalElements}
            page={filters.page}
            onPageChange={({ pageNumber, pageSize }) =>
              setFilters((prev) => ({
                ...prev,
                page: pageNumber,
                size: pageSize,
              }))
            }
            aria-label="Instruments"
          >
            <Column field="instrumentId" header="Instrument ID" />
            <Column field="name" header="Name" />
            <Column
              header="Type"
              body={(i: InstrumentSummary) => humanizeEnum(i.instrumentType)}
            />
            <Column field="exchangeMic" header="Exchange" />
            <Column field="currencyCode" header="Currency" />
            <Column
              header="Status"
              body={(i: InstrumentSummary) => <StatusBadge value={i.status} />}
            />
          </Table>
        )}
      </AsyncBoundary>
    </>
  );
}

function CurrenciesTab() {
  const query = useQuery((signal) => listCurrencies(signal), []);
  return (
    <AsyncBoundary
      loading={query.loading}
      error={query.error}
      data={query.data}
      onRetry={query.refetch}
      isEmpty={(list) => list.length === 0}
      emptyMessage="No currencies found."
    >
      {(list) => (
        <Table
          dataList={list}
          totalNum={0}
          onPageChange={() => {}}
          aria-label="Currencies"
        >
          <Column field="code" header="Code" />
          <Column field="name" header="Name" />
          <Column
            header="Minor units"
            body={(c: Currency) => c.minorUnits}
          />
        </Table>
      )}
    </AsyncBoundary>
  );
}

function ExchangesTab() {
  const query = useQuery((signal) => listExchanges(signal), []);
  return (
    <AsyncBoundary
      loading={query.loading}
      error={query.error}
      data={query.data}
      onRetry={query.refetch}
      isEmpty={(list) => list.length === 0}
      emptyMessage="No exchanges found."
    >
      {(list) => (
        <Table
          dataList={list}
          totalNum={0}
          onPageChange={() => {}}
          aria-label="Exchanges"
        >
          <Column field="mic" header="MIC" />
          <Column field="name" header="Name" />
          <Column
            header="Country"
            body={(x: Exchange) => x.country ?? "—"}
          />
        </Table>
      )}
    </AsyncBoundary>
  );
}

export default function ReferenceDataPage() {
  return (
    <div>
      <PageHeader
        title="Reference data"
        description="Instruments, currencies, and exchanges (read-only)."
      />
      <section className="panel" aria-label="Reference data">
        <TabView>
          <TabPanel header="Instruments">
            <InstrumentsTab />
          </TabPanel>
          <TabPanel header="Currencies">
            <CurrenciesTab />
          </TabPanel>
          <TabPanel header="Exchanges">
            <ExchangesTab />
          </TabPanel>
        </TabView>
      </section>
    </div>
  );
}
