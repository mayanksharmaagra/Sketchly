package com.jrprofessor.sketchly.di

import android.content.Context
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.jrprofessor.sketchly.data.local.ContactDao
import com.jrprofessor.sketchly.data.local.SketchlyDao
import com.jrprofessor.sketchly.data.local.SketchlyDatabase
import com.jrprofessor.sketchly.data.repository.AuthRepository
import com.jrprofessor.sketchly.data.repository.ContactRepository
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

    // ── Room Database ─────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideSketchlyDatabase(@ApplicationContext context: Context): SketchlyDatabase =
        SketchlyDatabase.getInstance(context)

    @Provides
    @Singleton
    fun provideSketchlyDao(db: SketchlyDatabase): SketchlyDao = db.sketchDao()

    @Provides
    @Singleton
    fun provideContactDao(db: SketchlyDatabase): ContactDao = db.contactDao()

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
        db: SketchlyDatabase,
    ): AuthRepository = AuthRepository(auth, firestore, functions, db)

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
        contactDao: ContactDao,
        firestore: FirebaseFirestore,
    ): ContactRepository = ContactRepository(contactDao, firestore)
}
