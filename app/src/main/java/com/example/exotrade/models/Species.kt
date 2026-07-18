package com.example.exotrade.models

import kotlinx.serialization.Serializable

@Serializable
data class Species(
    val id: Int,
    val scientificName: String,
    val commonName: String? = null,
    val family: String? = null,
    val order: String? = null,
    val speciesLsid: String? = null
)
