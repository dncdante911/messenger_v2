package com.worldmates.messenger.data.model

import com.google.gson.annotations.SerializedName

// ─── Business Directory ───────────────────────────────────────────────────────

data class BusinessDirectoryItem(
    @SerializedName("user_id")       val userId: Long,
    @SerializedName("username")      val username: String,
    @SerializedName("avatar")        val avatar: String?,
    @SerializedName("business_name") val businessName: String,
    @SerializedName("category")      val category: String,
    @SerializedName("description")   val description: String?,
    @SerializedName("address")       val address: String?,
    @SerializedName("phone")         val phone: String?,
    @SerializedName("email")         val email: String?,
    @SerializedName("website")       val website: String?,
    @SerializedName("is_verified")   val isVerified: Boolean,
    @SerializedName("distance")      val distance: Double? = null
)

data class BusinessDirectoryResponse(
    @SerializedName("api_status")  val status: Int,
    @SerializedName("businesses")  val businesses: List<BusinessDirectoryItem>,
    @SerializedName("total")       val total: Int,
    @SerializedName("page")        val page: Int
)

data class BusinessCategoriesResponse(
    @SerializedName("api_status")  val status: Int,
    @SerializedName("categories")  val categories: List<String>
)
