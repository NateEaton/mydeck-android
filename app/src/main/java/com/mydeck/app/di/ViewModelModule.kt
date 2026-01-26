package com.mydeck.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import com.mydeck.app.io.AssetLoader
import com.mydeck.app.io.AssetLoaderImpl

@Module
@InstallIn(ViewModelComponent::class)
interface ViewModelModule {
    @Binds
    fun bindAssetLoader(assetLoaderImpl: AssetLoaderImpl): AssetLoader
}
