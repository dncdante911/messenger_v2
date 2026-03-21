package com.worldmates.messenger.network

import com.worldmates.messenger.data.model.BusinessCategoriesResponse
import com.worldmates.messenger.data.model.BusinessDirectoryItem
import com.worldmates.messenger.data.model.BusinessDirectoryResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Business Directory API — public browsable catalogue of registered businesses.
 *
 * Auth: automatically via [NodeRetrofitClient] (header access-token).
 * Base path prefix: api/node/  (added by NodeRetrofitClient base URL + routes)
 */
interface NodeBusinessDirectoryApi {

    /**
     * List all businesses with pagination, optional category filter and search.
     */
    @GET("api/node/business-directory")
    suspend fun getBusinessDirectory(
        @Query("page")     page: Int = 1,
        @Query("limit")    limit: Int = 20,
        @Query("category") category: String? = null,
        @Query("search")   search: String? = null
    ): BusinessDirectoryResponse

    /**
     * List all unique business categories.
     */
    @GET("api/node/business-directory/categories")
    suspend fun getBusinessCategories(): BusinessCategoriesResponse

    /**
     * Get a single public business profile by userId.
     */
    @GET("api/node/business-directory/{userId}")
    suspend fun getBusinessProfile(
        @Path("userId") userId: Long
    ): Response<BusinessDirectoryItem>
}
