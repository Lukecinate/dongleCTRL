package com.suprcharg3d.dongleCTRL.protocol

import com.suprcharg3d.dongleCTRL.protocol.drivers.Cb1100AuProtocol
import com.suprcharg3d.dongleCTRL.protocol.drivers.CbProtocol
import com.suprcharg3d.dongleCTRL.protocol.drivers.Cx31993Protocol
import com.suprcharg3d.dongleCTRL.protocol.drivers.FiioBtProtocol
import com.suprcharg3d.dongleCTRL.protocol.drivers.FiioHidProtocol
import com.suprcharg3d.dongleCTRL.protocol.drivers.FiioRestmomentProtocol
import com.suprcharg3d.dongleCTRL.protocol.drivers.Gm55Protocol
import com.suprcharg3d.dongleCTRL.protocol.drivers.Kt1213Protocol
import com.suprcharg3d.dongleCTRL.protocol.drivers.KtProtocol

/**
 * Central registry mapping every [ProtocolFamily] to its template [DacProtocol] driver.
 * Registering a future protocol only requires adding an entry here.
 */
object ProtocolRegistry {

    @Volatile
    private var sharedFiioSchemas: FiioSchemaCatalog? = null

    fun installFiioSchemas(schemas: FiioSchemaCatalog) {
        sharedFiioSchemas = schemas
    }

    private val drivers: Map<ProtocolFamily, () -> DacProtocol> = mapOf(
        ProtocolFamily.CB to { CbProtocol(isSurround3dFamily = false) },
        ProtocolFamily.CB1300D to { CbProtocol(isSurround3dFamily = true) },
        ProtocolFamily.CB1100AU to { Cb1100AuProtocol() },
        ProtocolFamily.KT02H20 to { KtProtocol(isSixBand = false) },
        ProtocolFamily.KT0231H to { KtProtocol(isSixBand = true) },
        ProtocolFamily.KT1213 to { Kt1213Protocol() },
        ProtocolFamily.CX31993 to { Cx31993Protocol() },
        ProtocolFamily.GM55 to { Gm55Protocol() },
        ProtocolFamily.FIIO_HID to { FiioHidProtocol(ProtocolFamily.FIIO_HID, sharedFiioSchemas) },
        ProtocolFamily.FIIO_V2 to { FiioHidProtocol(ProtocolFamily.FIIO_V2, sharedFiioSchemas) },
        ProtocolFamily.FIIO_GIGAWIT to { FiioHidProtocol(ProtocolFamily.FIIO_GIGAWIT, sharedFiioSchemas) },
        ProtocolFamily.FIIO_RESTMOMENT to { FiioRestmomentProtocol() },
        ProtocolFamily.FIIO_BT_GAIA to { FiioBtProtocol(ProtocolFamily.FIIO_BT_GAIA, sharedFiioSchemas) },
        ProtocolFamily.FIIO_BT_UNIVERSAL to { FiioBtProtocol(ProtocolFamily.FIIO_BT_UNIVERSAL, sharedFiioSchemas) },
        ProtocolFamily.FIIO_BT_BR13 to { FiioBtProtocol(ProtocolFamily.FIIO_BT_BR13, sharedFiioSchemas) }
    )

    fun create(family: ProtocolFamily, fiioSchemas: FiioSchemaCatalog? = sharedFiioSchemas): DacProtocol {
        if (fiioSchemas != null && sharedFiioSchemas == null) {
            sharedFiioSchemas = fiioSchemas
        }
        return (drivers[family] ?: drivers.getValue(ProtocolFamily.CB)).invoke()
    }

    fun supportedFamilies(): List<ProtocolFamily> = ProtocolFamily.entries
}
