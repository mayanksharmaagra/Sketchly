package com.jrprofessor.sketchly.di

import android.content.Context
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
// V1: ContactDao hidden (contact Room logic removed)
// import com.jrprofessor.sketchly.data.local.ContactDao
import com.jrprofessor.sketchly.data.local.SketchlyDao
import com.jrprofessor.sketchly.data.local.SketchlyDatabase
import com.jrprofessor.sketchly.data.repository.AuthRepository
import com.jrprofessor.sketchly.data.repository.ContactRepository
import com.jrprofessor.sketchly.data.repository.EditProfileRepository
import com.jrprofessor.sketchly.data.repository.SketchlyRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ── Firebase ──────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFunctions(): FirebaseFunctions =
        FirebaseFunctions.getInstance("us-central1")

    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage =
        FirebaseStorage.getInstance("gs://sketchly-44c83.firebasestorage.app")

    // ── Room Database ──────────────────────────────────────────────────
    // SketchlyDatabase + SketchlyDao: ACTIVE (needed for SketchlyRepository / draft)
    // ContactDao: V1 HIDDEN (contact Room logic removed)

    @Provides
    @Singleton
    fun provideSketchlyDatabase(@ApplicationContext context: Context): SketchlyDatabase =
        SketchlyDatabase.getInstance(context)

    @Provides
    @Singleton
    fun provideSketchlyDao(db: SketchlyDatabase): SketchlyDao = db.sketchDao()

    // V1: ContactDao hidden — preserved for V2
    // @Provides @Singleton
    // fun provideContactDao(db: SketchlyDatabase): ContactDao = db.contactDao()

    // ── WorkManager ───────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)

    // ── Repositories ──────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideAuthRepository(
        auth: FirebaseAuth,
        firestore: FirebaseFirestore,
        functions: FirebaseFunctions,
        // V1: Room DB hidden — db: SketchlyDatabase,
    ): AuthRepository = AuthRepository(auth, firestore, functions)

    @Provides
    @Singleton
    fun provideSketchlyRepository(
        sketchDao: SketchlyDao,
        firestore: FirebaseFirestore,
        workManager: WorkManager,
    ): SketchlyRepository = SketchlyRepository(sketchDao, firestore, workManager)

    @Provides
    @Singleton
    fun provideContactRepository(
        @ApplicationContext context: Context,
        // V1: Room ContactDao hidden — contactDao: ContactDao,
        firestore: FirebaseFirestore,
        auth: FirebaseAuth,
        functions: FirebaseFunctions,
    ): ContactRepository = ContactRepository(firestore, auth, functions, context)

    @Provides
    @Singleton
    fun provideEditProfileRepository(
        auth: FirebaseAuth,
        firestore: FirebaseFirestore,
        storage: FirebaseStorage,
    ): EditProfileRepository = EditProfileRepository(auth, firestore, storage)
}
