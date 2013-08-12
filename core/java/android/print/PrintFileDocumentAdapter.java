/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.print;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.CancellationSignal.OnCancelListener;
import android.util.Log;

import com.android.internal.R;

import libcore.io.IoUtils;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Adapter for printing files. This class could be useful if you
 * want to print a file and intercept when the system is ready
 * spooling the data, so you can deleted the file if it is a
 * temporary one.
 */
public class PrintFileDocumentAdapter extends PrintDocumentAdapter {

    private static final String LOG_TAG = "PrintedFileDocumentAdapter";

    private final Context mContext;

    private final File mFile;

    private final PrintDocumentInfo mDocumentInfo;

    private WriteFileAsyncTask mWriteFileAsyncTask;

    /**
     * Constructor.
     *
     * @param context Context for accessing resources.
     * @param file The file to print.
     * @param documentInfo The information about the printed file.
     */
    public PrintFileDocumentAdapter(Context context, File file,
            PrintDocumentInfo documentInfo) {
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null!");
        }
        if (documentInfo == null) {
            throw new IllegalArgumentException("documentInfo cannot be null!");
        }
        mContext = context;
        mFile = file;
        mDocumentInfo = documentInfo;
    }

    @Override
    public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
            CancellationSignal cancellationSignal, LayoutResultCallback callback,
            Bundle metadata) {
        callback.onLayoutFinished(mDocumentInfo, false);
    }

    @Override
    public void onWrite(PageRange[] pages, FileDescriptor destination,
            CancellationSignal cancellationSignal, WriteResultCallback callback) {
        mWriteFileAsyncTask = new WriteFileAsyncTask(destination, cancellationSignal, callback);
        mWriteFileAsyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
                (Void[]) null);
    }

    private final class WriteFileAsyncTask extends AsyncTask<Void, Void, Void> {

        private final FileDescriptor mDestination;

        private final WriteResultCallback mResultCallback;

        private final CancellationSignal mCancellationSignal;

        public WriteFileAsyncTask(FileDescriptor destination,
                CancellationSignal cancellationSignal, WriteResultCallback callback) {
            mDestination = destination;
            mResultCallback = callback;
            mCancellationSignal = cancellationSignal;
            mCancellationSignal.setOnCancelListener(new OnCancelListener() {
                @Override
                public void onCancel() {
                    cancel(true);
                }
            });
        }

        @Override
        protected Void doInBackground(Void... params) {
            InputStream in = null;
            OutputStream out = new FileOutputStream(mDestination);
            final byte[] buffer = new byte[8192];
            try {
                in = new FileInputStream(mFile);
                while (true) {
                    if (isCancelled()) {
                        break;
                    }
                    final int readByteCount = in.read(buffer);
                    if (readByteCount < 0) {
                        break;
                    }
                    out.write(buffer, 0, readByteCount);
                }
             } catch (IOException ioe) {
                 Log.e(LOG_TAG, "Error writing data!", ioe);
                 mResultCallback.onWriteFailed(mContext.getString(
                         R.string.write_fail_reason_cannot_write));
             } finally {
                IoUtils.closeQuietly(in);
                IoUtils.closeQuietly(out);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            mResultCallback.onWriteFinished(new PageRange[] {PageRange.ALL_PAGES});
        }

        @Override
        protected void onCancelled(Void result) {
            mResultCallback.onWriteFailed(mContext.getString(
                    R.string.write_fail_reason_cancelled));
        }
    }
}

