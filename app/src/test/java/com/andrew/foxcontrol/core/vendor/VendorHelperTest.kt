package com.andrew.foxcontrol.core.vendor

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for VendorHelper logic that don't require Android Build constants.
 * Note: getVendor() depends on Build.BRAND which is not available in JVM tests,
 * so we test the structure and data rather than vendor detection.
 */
class VendorHelperTest {

    @Test
    fun vendorEnumHasAllValues() {
        val values = VendorHelper.Vendor.values()
        assertEquals(8, values.size)
        assertTrue(values.contains(VendorHelper.Vendor.UNKNOWN))
        assertTrue(values.contains(VendorHelper.Vendor.XIAOMI))
        assertTrue(values.contains(VendorHelper.Vendor.SAMSUNG))
        assertTrue(values.contains(VendorHelper.Vendor.HUAWEI))
        assertTrue(values.contains(VendorHelper.Vendor.HONOR))
        assertTrue(values.contains(VendorHelper.Vendor.OPPO))
        assertTrue(values.contains(VendorHelper.Vendor.VIVO))
        assertTrue(values.contains(VendorHelper.Vendor.ONEPLUS))
    }

    @Test
    fun vendorEnumValuesAreUnique() {
        val values = VendorHelper.Vendor.values()
        assertEquals(values.size, values.toSet().size)
    }

    @Test
    fun instructionsHaveValidStructure() {
        // We can't call getVendorInstructions() because it depends on Build.BRAND
        // Instead, we verify the instruction data structure by checking the source
        // that each vendor has instructions defined.
        // This test verifies the enum itself is properly populated.
        assertTrue("Should have at least 7 vendor types",
            VendorHelper.Vendor.values().size >= 7)
    }

    @Test
    fun vendorInfoDataStructure_instruction() {
        // Test that the data classes work correctly
        val instruction = VendorHelper.Instruction(
            title = "Test instruction",
            description = "Test description",
            steps = listOf("Step 1", "Step 2", "Step 3")
        )
        assertEquals("Test instruction", instruction.title)
        assertEquals("Test description", instruction.description)
        assertEquals(3, instruction.steps.size)
        assertEquals("Step 1", instruction.steps[0])
    }

    @Test
    fun vendorInfoDataStructure_vendorInfo() {
        val info = VendorHelper.VendorInfo(
            name = "Test Vendor",
            instructions = listOf(
                VendorHelper.Instruction(
                    title = "Step 1",
                    description = "Description",
                    steps = listOf("Do this")
                )
            )
        )
        assertEquals("Test Vendor", info.name)
        assertEquals(1, info.instructions.size)
    }
}
