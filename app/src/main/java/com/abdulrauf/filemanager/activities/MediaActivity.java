package com.abdulrauf.filemanager.activities;

import android.annotation.TargetApi;
import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by abdul on 8/3/16.
 */
public class MediaActivity extends AppCompatActivity {

    private String SOURCE_PATH;
    private Player player = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try{
            SOURCE_PATH = getIntent().getStringExtra("SOURCE_PATH");
        } catch (Exception e) {
            Toast.makeText(MediaActivity.this,"Intent not found", Toast.LENGTH_SHORT).show();
            finish();
        }

        SurfaceView surfaceView = new SurfaceView(this);
        surfaceView.getHolder().addCallback(surfaceViewCallback);
        setContentView(surfaceView);
    }


    private SurfaceHolder.Callback surfaceViewCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Toast.makeText(MediaActivity.this,"Surface Created ", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Toast.makeText(MediaActivity.this,"Surface Changed ", Toast.LENGTH_SHORT).show();
            if(player == null) {
                player = new Player(SOURCE_PATH, holder.getSurface());
                player.execute();
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {

            if(player != null)
                player.cancel(true);
            Toast.makeText(MediaActivity.this,"Surface Destroyed", Toast.LENGTH_SHORT).show();
        }
    };


    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private class Player extends AsyncTask<Void, Void, Void> {

        private MediaExtractor mediaExtractor;
        private MediaCodec mediaCodec;
        private Surface surface;
        private String sourcePath;

        public Player(String path, Surface surface) {
            this.sourcePath = path;
            this.surface = surface;
        }


        @Override
        protected Void doInBackground(Void... params) {

            mediaExtractor = new MediaExtractor();
            try {
                mediaExtractor.setDataSource(sourcePath);
            } catch (IOException e) {
                e.printStackTrace();
            }

            for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
                MediaFormat format = mediaExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("video/")) {
                    mediaExtractor.selectTrack(i);
                    try {
                        mediaCodec = MediaCodec.createDecoderByType(mime);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    mediaCodec.configure(format, surface, null, 0);
                    break;
                }
            }

            if (mediaCodec == null) {
                this.cancel(true);
            }

            mediaCodec.start();

            ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
            ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean isEOS = false;
            long startTime = System.currentTimeMillis();

            while (!Thread.interrupted()) {
                if (!isEOS) {
                    int inIndex = mediaCodec.dequeueInputBuffer(10000);
                    if (inIndex >= 0) {
                        ByteBuffer buffer = inputBuffers[inIndex];
                        int sampleSize = mediaExtractor.readSampleData(buffer, 0);
                        if (sampleSize < 0) {

                            mediaCodec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            isEOS = true;
                        } else {
                            mediaCodec.queueInputBuffer(inIndex, 0, sampleSize, mediaExtractor.getSampleTime(), 0);
                            mediaExtractor.advance();
                        }
                    }
                }

                int outIndex = mediaCodec.dequeueOutputBuffer(info, 10000);
                switch (outIndex) {
                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                        outputBuffers = mediaCodec.getOutputBuffers();
                        break;

                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                        break;

                    case MediaCodec.INFO_TRY_AGAIN_LATER:
                        break;

                    default:
                        ByteBuffer buffer = outputBuffers[outIndex];

                        while (info.presentationTimeUs / 1000 > System.currentTimeMillis() - startTime) {
                            try {
                                Thread.sleep(10);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                                break;
                            }
                        }
                        mediaCodec.releaseOutputBuffer(outIndex, true);
                        break;
                }

                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break;
                }
            }

            mediaCodec.stop();
            mediaCodec.release();
            mediaExtractor.release();

            return null;
        }

        private void init() {

        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            finish();
        }
    }

}
