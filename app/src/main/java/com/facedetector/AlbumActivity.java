package com.facedetector;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import com.facedetector.customview.GridItem;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class AlbumActivity extends AppCompatActivity {

    private GridView gridView;
    private String openPath; //页面工作路径
    private Bitmap mPlaceHolderBitmap;
    private ArrayList<String> imagePaths; //当前页面下的图片路径
    private AdapterView.OnItemClickListener onClickListener= new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Log.d("", "你点击了~" + position + "~项");
            Intent intent = new Intent(AlbumActivity.this ,PreviewActivity.class);
            intent.putExtra("path",imagePaths.get(position));
            startActivity(intent);
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_album);
        gridView = findViewById(R.id.gridView);
        mPlaceHolderBitmap = BitmapFactory.decodeResource(getResources(),R.drawable.circle);

        Intent intent = getIntent();
        openPath = intent.getStringExtra("path");

        imagePaths = getImgListByDir(openPath);
        BaseAdapter adapter = new MyAdapter(this,imagePaths);
        gridView.setAdapter(adapter);
        gridView.setOnItemClickListener(onClickListener);
    }

    /**
     * 通过图片文件夹的路径获取该目录下的图片
     */
    public ArrayList<String> getImgListByDir(String dir) {
        ArrayList<String> imgPaths = new ArrayList<>();
        File directory = new File(dir);
        if (directory == null || !directory.exists()) {
            return imgPaths;
        }
        File[] files = directory.listFiles();
        for (File file : files) {
            String path = file.getAbsolutePath();
            if (path.endsWith(".jpg")||path.endsWith(".png")) {
                imgPaths.add(path);
            }
        }
        return imgPaths;
    }

    public void loadBitmap(String path, ImageView imageView) {
        if (cancelPotentialWork(path, imageView)) {
            final BitmapWorkerTask task = new BitmapWorkerTask(imageView);
            final AsyncDrawable asyncDrawable =
                    new AsyncDrawable(getResources(), mPlaceHolderBitmap, task);
            imageView.setImageDrawable(asyncDrawable);
            task.execute(path);
        }

    }

    public static boolean cancelPotentialWork(String path, ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

        if (bitmapWorkerTask != null) {
            final String bitmapData = bitmapWorkerTask.path;
            if (bitmapData != path) {
                // 取消之前的任务
                bitmapWorkerTask.cancel(true);
            } else {
                // 相同任务已经存在，直接返回false，不再进行重复的加载
                return false;
            }
        }
        // 没有Task和ImageView进行绑定，或者Task由于加载资源不同而被取消，返回true
        return true;
    }

    private static BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
        if (imageView != null) {
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof AsyncDrawable) {
                final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
                return asyncDrawable.getBitmapWorkerTask();
            }
        }
        return null;
    }

    private class MyAdapter extends BaseAdapter{
        class ViewHolder{
            ImageView imageView;
        }

        private Context context;
        private ArrayList<String> imagePaths;

        public MyAdapter(Context context, ArrayList<String> imagePaths){
            super();
            this.context = context;
            this.imagePaths = imagePaths;
        }

        @Override
        public int getCount() {
            return imagePaths.size();
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        // 4
        @Override
        public Object getItem(int position) {
            return null;
        }

        // 5
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final String path = imagePaths.get(position);
            ViewHolder holder;
            if (convertView == null) {
                final LayoutInflater layoutInflater = LayoutInflater.from(context);
                convertView = layoutInflater.inflate(R.layout.grid_item, null);
                holder  = new ViewHolder();
                holder.imageView = convertView.findViewById(R.id.previewIcon);
                convertView.setTag(holder);
            }else{
                holder = (ViewHolder) convertView.getTag();
            }

            loadBitmap(path,holder.imageView);

            return convertView;
        }
    }

    static class AsyncDrawable extends BitmapDrawable {
        private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

        public AsyncDrawable(Resources res, Bitmap bitmap,
                             BitmapWorkerTask bitmapWorkerTask) {
            super(res, bitmap);
            bitmapWorkerTaskReference =
                    new WeakReference<BitmapWorkerTask>(bitmapWorkerTask);
        }

        public BitmapWorkerTask getBitmapWorkerTask() {
            return bitmapWorkerTaskReference.get();
        }
    }

    class BitmapWorkerTask extends AsyncTask<String, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewReference;
        public String path;

        public BitmapWorkerTask(ImageView imageView) {
            // Use a WeakReference to ensure the ImageView can be garbage collected
            imageViewReference = new WeakReference<ImageView>(imageView);
        }


        public Bitmap decodeSampledBitmapFromPath(String path,
                                                      int reqWidth, int reqHeight) {

            Bitmap bitmap = BitmapFactory.decodeFile(path);

            Bitmap bmp1 = ThumbnailUtils.extractThumbnail(bitmap, reqWidth,
                    reqHeight);
            return bmp1;
        }


        // Decode image in background.
        @Override
        protected Bitmap doInBackground(String... params) {
            path = params[0];
            int MIN_SIZE = 300;
            return decodeSampledBitmapFromPath(path,MIN_SIZE,MIN_SIZE);
        }

        // Once complete, see if ImageView is still around and set bitmap.
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (isCancelled()) {
                bitmap = null;
            }

            if (imageViewReference != null && bitmap != null) {
                final ImageView imageView = imageViewReference.get();
                final BitmapWorkerTask bitmapWorkerTask =
                        getBitmapWorkerTask(imageView);
                if (this == bitmapWorkerTask && imageView != null) {
                    imageView.setImageBitmap(bitmap);
                }
            }
        }
    }

}
