package com.tradingcapture.allocation;

import org.junit.jupiter.api.Test;
import quickfix.DataDictionary;
import quickfix.field.AllocAccount;
import quickfix.field.AllocID;
import quickfix.field.AllocNoOrdersType;
import quickfix.field.AllocQty;
import quickfix.field.AllocTransType;
import quickfix.field.AllocType;
import quickfix.field.AvgPx;
import quickfix.field.ClOrdID;
import quickfix.field.Quantity;
import quickfix.field.Side;
import quickfix.field.TradeDate;
import quickfix.fix44.AllocationInstruction;

import java.io.InputStream;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2 compatibility spike (ADR: version matrix): proves QuickFIX/J 2.3.2 on the
 * Java 21 toolchain can (1) load the migrated FIX 4.4 data dictionary, (2) build an
 * AllocationInstruction (35=J) with repeating groups, and (3) round-trip it through
 * the wire format with dictionary validation. The allocation engine's real parsing
 * pipeline (Phase 7) builds on exactly these operations.
 */
class FixDictionarySpikeTest {

    @Test
    void loadsMigratedFix44DictionaryFromClasspath() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("fix44.xml")) {
            assertThat(in).as("fix44.xml must be on the allocation-engine classpath").isNotNull();
            DataDictionary dictionary = new DataDictionary(in);
            assertThat(dictionary.getVersion()).isEqualTo("FIX.4.4");
            assertThat(dictionary.isMsgType("J")).as("AllocationInstruction (35=J) defined").isTrue();
            assertThat(dictionary.getFieldName(70)).isEqualTo("AllocID");
        }
    }

    @Test
    void buildsAndReparsesAllocationInstructionWithRepeatingGroups() throws Exception {
        AllocationInstruction built = new AllocationInstruction(
                new AllocID("ALLOC-0001"),
                new AllocTransType(AllocTransType.NEW),
                new AllocType(AllocType.CALCULATED),
                new AllocNoOrdersType(AllocNoOrdersType.EXPLICIT_LIST_PROVIDED),
                new Side(Side.BUY),
                new Quantity(100),
                new AvgPx(new BigDecimal("101.25").doubleValue()),
                new TradeDate("20260714"));

        AllocationInstruction.NoOrders orderGroup = new AllocationInstruction.NoOrders();
        orderGroup.set(new ClOrdID("ORD-1"));
        built.addGroup(orderGroup);

        AllocationInstruction.NoAllocs allocGroup = new AllocationInstruction.NoAllocs();
        allocGroup.set(new AllocAccount("ACC-1"));
        allocGroup.set(new AllocQty(100));
        built.addGroup(allocGroup);

        String wire = built.toString();
        assertThat(wire).contains("35=J");

        DataDictionary dictionary;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("fix44.xml")) {
            dictionary = new DataDictionary(in);
        }
        AllocationInstruction reparsed = new AllocationInstruction();
        reparsed.fromString(wire, dictionary, true);

        assertThat(reparsed.getAllocID().getValue()).isEqualTo("ALLOC-0001");
        assertThat(reparsed.getGroupCount(quickfix.field.NoOrders.FIELD)).isEqualTo(1);
        AllocationInstruction.NoOrders reparsedOrder = new AllocationInstruction.NoOrders();
        reparsed.getGroup(1, reparsedOrder);
        assertThat(reparsedOrder.getClOrdID().getValue()).isEqualTo("ORD-1");
        AllocationInstruction.NoAllocs reparsedAlloc = new AllocationInstruction.NoAllocs();
        reparsed.getGroup(1, reparsedAlloc);
        assertThat(reparsedAlloc.getAllocAccount().getValue()).isEqualTo("ACC-1");
    }
}
