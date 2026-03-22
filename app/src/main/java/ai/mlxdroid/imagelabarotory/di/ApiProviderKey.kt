package ai.mlxdroid.imagelabarotory.di

import ai.mlxdroid.imagelabarotory.data.model.ApiProvider
import dagger.MapKey

@MapKey
annotation class ApiProviderKey(val value: ApiProvider)
