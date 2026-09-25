package com.wickedcoder.wifilens.core.model

/** A wall's material, and the flat signal loss (dB) a straight-line Wi-Fi path suffers crossing one
 * cell of it — see [predictRssi]. Fixed per-material constants rather than a physical model of
 * thickness/density or frequency-dependent attenuation: this app labels its output "predicted",
 * not measured, and this is the level of fidelity that promise is built on. */
sealed interface Material {
    val lossDb: Float

    data object Drywall : Material {
        override val lossDb: Float = 3f
    }

    data object Wood : Material {
        override val lossDb: Float = 4f
    }

    data object Glass : Material {
        override val lossDb: Float = 2f
    }

    data object Brick : Material {
        override val lossDb: Float = 6f
    }

    data object Concrete : Material {
        override val lossDb: Float = 8f
    }

    data object Metal : Material {
        override val lossDb: Float = 12f
    }
}
