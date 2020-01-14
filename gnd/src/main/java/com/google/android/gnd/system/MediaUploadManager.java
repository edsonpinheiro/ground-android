/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.gnd.system;

import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;

// TODO: Add column to Observation table for storing uploaded media urls
// TODO: Synced to remote db as well
@Singleton
public class MediaUploadManager {

  private static final String TAG = MediaUploadManager.class.getName();
  private static final String MEDIA_ROOT_DIR = "uploaded_media";
  private final SimpleDateFormat DATE_FORMAT =
      new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());

  @Inject
  MediaUploadManager() {}

  /** Returns a reference to the default Storage bucket. */
  private FirebaseStorage getStorage() {
    return FirebaseStorage.getInstance();
  }

  /** Returns a reference to the root media dir. */
  private StorageReference getRootMediaDir() {
    return getStorage().getReference().child(MEDIA_ROOT_DIR);
  }

  /**
   * Returns a reference to a object under the root media dir.
   *
   * @param fileName Name of the uploaded media
   */
  private StorageReference createReference(String fileName) {
    return getRootMediaDir().child(fileName + '-' + getFilenameSuffix());
  }

  /** Converts current timestamp to a string to be used a suffix for uploading media. */
  private String getFilenameSuffix() {
    return DATE_FORMAT.format(new Date());
  }

  public void uploadMediaFromFile(File file, String fileName) {
    StorageReference reference = createReference(fileName);
    UploadTask task = reference.putFile(Uri.fromFile(file));

    uploadMediaToFirebaseStorage(task, fileName);
    fetchDownloadUrl(reference, task);
  }

  public void uploadMediaFromBitmap(Bitmap bitmap, String fileName) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
    byte[] data = baos.toByteArray();

    StorageReference reference = createReference(fileName);
    UploadTask task = reference.putBytes(data);

    uploadMediaToFirebaseStorage(task, fileName);
    fetchDownloadUrl(reference, task);
  }

  private void uploadMediaToFirebaseStorage(UploadTask uploadTask, String fileName) {
    // TODO: Create UploadState enum and use RxJava to broadcast upload state globally.
    uploadTask
        .addOnCanceledListener(
            () -> {
              Log.d(TAG, String.format("Uploading canceled: %s", fileName));
            })
        .addOnCompleteListener(
            task -> {
              Log.d(TAG, String.format("Uploading completed: %s", fileName));
            })
        .addOnFailureListener(
            e -> {
              Log.d(TAG, String.format("Uploading failed: %s", fileName), e);
            })
        .addOnSuccessListener(
            taskSnapshot -> {
              Log.d(TAG, String.format("Uploading succeeded: %s", fileName));
            })
        .addOnProgressListener(
            taskSnapshot -> {
              double percentCompleted =
                  100.0 * taskSnapshot.getBytesTransferred() / taskSnapshot.getTotalByteCount();
              Log.d(TAG, String.format("Uploading in progress: %s %f", fileName, percentCompleted));
            });
  }

  private void fetchDownloadUrl(StorageReference reference, UploadTask uploadTask) {
    uploadTask
        .continueWithTask(
            task -> {
              if (!task.isSuccessful()) {
                throw Objects.requireNonNull(task.getException());
              }
              // Continue with the task to get the download URL
              return reference.getDownloadUrl();
            })
        .addOnCompleteListener(
            task -> {
              // TODO save to local database
              if (task.isSuccessful()) {
                Uri downloadUri = task.getResult();
                Log.d(TAG, String.format("Uploaded to : %s", downloadUri));
              }
            });
  }
}
