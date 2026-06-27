package com.velogappie.app.ui

data class BikeModel(val name: String, val colors: List<BikeColor>)
data class BikeColor(val name: String, val searchQuery: String)

val VELORETTI_MODELS = listOf(
    BikeModel("Ivy Two", listOf(
        BikeColor("Matte Black", "Veloretti Ivy Two Matte Black"),
        BikeColor("Dark Grey", "Veloretti Ivy Two Dark Grey"),
        BikeColor("Ivory", "Veloretti Ivy Two Ivory"),
        BikeColor("Forest Green", "Veloretti Ivy Two Forest Green"),
    )),
    BikeModel("Ivy Two Pro", listOf(
        BikeColor("Matte Black", "Veloretti Ivy Two Pro Matte Black"),
        BikeColor("Dark Grey", "Veloretti Ivy Two Pro Dark Grey"),
        BikeColor("Ivory", "Veloretti Ivy Two Pro Ivory"),
        BikeColor("Forest Green", "Veloretti Ivy Two Pro Forest Green"),
    )),
    BikeModel("Ace Two", listOf(
        BikeColor("Matte Black", "Veloretti Ace Two Matte Black"),
        BikeColor("Dark Grey", "Veloretti Ace Two Dark Grey"),
        BikeColor("Ivory", "Veloretti Ace Two Ivory"),
        BikeColor("Forest Green", "Veloretti Ace Two Forest Green"),
    )),
    BikeModel("Ace Two Pro", listOf(
        BikeColor("Matte Black", "Veloretti Ace Two Pro Matte Black"),
        BikeColor("Dark Grey", "Veloretti Ace Two Pro Dark Grey"),
        BikeColor("Ivory", "Veloretti Ace Two Pro Ivory"),
        BikeColor("Forest Green", "Veloretti Ace Two Pro Forest Green"),
    )),
)
