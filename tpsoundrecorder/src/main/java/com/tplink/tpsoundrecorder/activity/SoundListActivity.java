
package com.tplink.tpsoundrecorder.activity;

import android.annotation.SuppressLint;
import android.app.AlertDialog.Builder;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.hardware.SensorManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.tplink.tpsoundrecorder.R;
import com.tplink.tpsoundrecorder.service.SoundRecorderService;
import com.tplink.tpsoundrecorder.util.AndroidUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public class SoundListActivity extends AppCompatActivity {

    private ListView mLv;

    private List<File> mFileList = new ArrayList<File>();

    private List<File> mFileDeleteList = new ArrayList<File>();

    private View mEmptyView;

    private Context mContext;

    private Toolbar toolbar;

    private boolean isSelectionMode = false;

    private static final int NOSELECT_STATE = -1;

    private RecordingListAdapter mAdapter;

    private Menu mMenu;

    private final int DELETE_MENU_ITEM = 0;


    private static final int DELETE_FILES_MESSAGE = 100;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == DELETE_FILES_MESSAGE) {
                isSelectionMode = false;

                int deletSize = mFileDeleteList.size();
                for (int i = 0; i < deletSize; i++) {
                    File file = mFileDeleteList.get(i);
                    if (file != null && file.delete()) {
                        MediaScannerConnection.scanFile(mContext, new String[] {
                                file.getAbsolutePath()
                        }, null, null);
                    }
                }
                mFileDeleteList.clear();
                loadSoundList();
                mMenu.getItem(DELETE_MENU_ITEM).setVisible(false);
                mAdapter = new RecordingListAdapter(mContext, mFileList, NOSELECT_STATE);
                mLv.setAdapter(mAdapter);
                String listTitle = mContext.getResources().getString(R.string.record_list);// "List"
                toolbar.setTitle(listTitle);
                updateEmptyView();
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;

        setContentView(R.layout.activity_sound_list);

        mEmptyView = findViewById(R.id.tv_empty);
        mLv = (ListView)findViewById(R.id.lv);
        toolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        initToolbar();

        loadSoundList();

        mAdapter = new RecordingListAdapter(mContext, mFileList, NOSELECT_STATE);
        mLv.setAdapter(mAdapter);

        updateEmptyView();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        SoundListActivity.this.getMenuInflater().inflate(R.menu.delete_menu, menu);
        mMenu = menu;
        mMenu.getItem(DELETE_MENU_ITEM).setVisible(true);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        initToolbar();
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_delete:
                if (mFileDeleteList.size() > 0) {
                    Builder builder = new Builder(mContext, R.style.AlertDialogTheme);
                    builder.setTitle(R.string.action_delete);
                    if (mFileDeleteList.size() <= 1) {
                        builder.setMessage(mContext.getResources()
                                .getQuantityString(R.plurals.delete_opration_confirm, 1));
                    } else {
                        builder.setMessage(mContext.getResources()
                                .getQuantityString(R.plurals.delete_opration_confirm, 2));
                    }
                    builder.setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    // TODO Auto-generated method stub
                                    mHandler.sendEmptyMessage(DELETE_FILES_MESSAGE);
                                    dialog.dismiss();
                                }
                            });
                    builder.setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                    builder.create().show();
                }
                return true;
        }
        return false;
    }

    private void initToolbar() {
        toolbar.setNavigationIcon(R.drawable.list_back_selector);
        toolbar.setNavigationOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                handleBack();
            }
        });
    }

    private void handleBack() {
        if (!isSelectionMode) {
            SoundListActivity.this.finish();
        } else {
            isSelectionMode = false;
            for (int i = 0; i < mFileList.size(); i++) {
                mAdapter.isCheckBoxVisible.put(i, CheckBox.INVISIBLE);
            }
            mMenu.getItem(DELETE_MENU_ITEM).setVisible(false);
            mAdapter = new RecordingListAdapter(mContext, mFileList, NOSELECT_STATE);
            mLv.setAdapter(mAdapter);
            String listTitle = mContext.getResources().getString(R.string.record_list);// "List"
            toolbar.setTitle(listTitle);
        }
    }

    private void updateEmptyView() {
        if (mFileList.size() > 0) {
            mEmptyView.setVisibility(View.INVISIBLE);
        } else {
            mEmptyView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // 列表选择模式下，才消费KEYCODE_BACK
        if (isSelectionMode && event.getAction() == KeyEvent.ACTION_UP
                && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            handleBack();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void loadSoundList() {
        String sdPath = null;
        String internalPath = null;
        mFileList.clear();

        if (AndroidUtil.getSDState(mContext) != null
                && AndroidUtil.getSDState(mContext).equals(Environment.MEDIA_MOUNTED)) {
            if (getResources().getBoolean(R.bool.config_storage_path)) {
                sdPath = AndroidUtil.getSDPath(mContext) + File.separator
                        + SoundRecorderService.FOLDER_NAME;
            } else {
                sdPath = AndroidUtil.getSDPath(mContext) + File.separator
                        + SoundRecorderService.FOLDER_NAME;
            }
        }

        internalPath = AndroidUtil.getPhoneLocalStoragePath(mContext) + File.separator
                + SoundRecorderService.FOLDER_NAME;

        if (sdPath != null) {
            File sdDir = new File(sdPath);
            File[] soundFileList = sdDir.listFiles();
            if (soundFileList != null) {
                for (File file : soundFileList) {
                    if (file.getName().endsWith(".aac") || file.getName().endsWith(".wav")
                            || file.getName().endsWith(".amr")) {
                        mFileList.add(file);
                    }
                }
            }
        }

        if (internalPath != null) {
            File internalDir = new File(internalPath);
            File[] soundFileList = internalDir.listFiles();
            if (soundFileList != null) {
                for (File file : soundFileList) {
                    if (file.getName().endsWith(".aac") || file.getName().endsWith(".wav")
                            || file.getName().endsWith(".amr")) {
                        mFileList.add(file);
                    }
                }
            }
        }

        /**
         * 按照时间降序排列
         */
        Collections.sort(mFileList, new Comparator<File>() {

            @Override
            public int compare(File lhs, File rhs) {
                return (int)(rhs.lastModified() - lhs.lastModified());
            }
        });
    }

    private class RecordingListAdapter extends BaseAdapter {

        private List<File> mList;

        private LayoutInflater inflater;

        private HashMap<Integer, Integer> isCheckBoxVisible;

        private HashMap<Integer, Boolean> isChecked;

        @SuppressLint("UseSparseArrays")
        public RecordingListAdapter(Context context, List<File> list, int position) {
            // TODO Auto-generated constructor stub
            inflater = LayoutInflater.from(context);
            mList = list;
            isCheckBoxVisible = new HashMap<Integer, Integer>();
            isChecked = new HashMap<Integer, Boolean>();

            if (isSelectionMode) {
                for (int i = 0; i < list.size(); i++) {
                    isCheckBoxVisible.put(i, CheckBox.VISIBLE);
                    isChecked.put(i, false);
                }
            } else {
                for (int i = 0; i < list.size(); i++) {
                    isCheckBoxVisible.put(i, CheckBox.INVISIBLE);
                    isChecked.put(i, false);
                }
            }

            if (isSelectionMode && position >= 0) {
                isChecked.put(position, true);
            }
        }

        @Override
        public int getCount() {
            // TODO Auto-generated method stub
            return mList.size();
        }

        @Override
        public Object getItem(int position) {
            // TODO Auto-generated method stub
            return mList.get(position);
        }

        @Override
        public long getItemId(int position) {
            // TODO Auto-generated method stub
            return 0;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            // TODO Auto-generated method stub
            final ViewHolder viewHolder;
            if (convertView == null) {
                viewHolder = new ViewHolder();
                convertView = inflater.inflate(R.layout.item_sound, null);
                viewHolder.nameView = (TextView)convertView.findViewById(R.id.tv_file_name);
                viewHolder.checkBox = (CheckBox)convertView.findViewById(R.id.cb_select);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder)convertView.getTag();
            }
            final File file = mList.get(position);
            viewHolder.nameView.setText(file.getName());
            viewHolder.checkBox.setChecked(isChecked.get(position));
            viewHolder.checkBox.setVisibility(isCheckBoxVisible.get(position));

            convertView.setOnLongClickListener(new onMyLongClick(position, mList));
            convertView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    // TODO Auto-generated method stub
                    if (isSelectionMode) {
                        if (viewHolder.checkBox.isChecked()) {
                            viewHolder.checkBox.setChecked(false);
                            isChecked.put(position, false);
                            mFileDeleteList.remove(file);
                            if (mFileDeleteList.size() == 0) {
                                isSelectionMode = false;
                                for (int i = 0; i < mList.size(); i++) {
                                    mAdapter.isCheckBoxVisible.put(i, CheckBox.INVISIBLE);
                                }
                                mMenu.getItem(DELETE_MENU_ITEM).setVisible(false);
                                mAdapter = new RecordingListAdapter(mContext, mList,
                                        NOSELECT_STATE);
                                mLv.setAdapter(mAdapter);
                                String listTitle = mContext.getResources()
                                        .getString(R.string.record_list);
                                toolbar.setTitle(listTitle);
                            } else {
                                String title = mContext.getResources()
                                        .getString(R.string.num_of_selected);
                                String.format(title, mFileDeleteList.size());
                                toolbar.setTitle(String.format(title, mFileDeleteList.size()));
                            }
                        } else {
                            viewHolder.checkBox.setChecked(true);
                            isChecked.put(position, true);
                            mFileDeleteList.add(file);
                            String title = mContext.getResources()
                                    .getString(R.string.num_of_selected);
                            String.format(title, mFileDeleteList.size());
                            toolbar.setTitle(String.format(title, mFileDeleteList.size()));
                        }
                    } else {
                        try {
                            Intent intent = new Intent();
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.setAction(Intent.ACTION_VIEW);
                            intent.setDataAndType(Uri.fromFile(file), "audio/*");
                            startActivity(intent);
                        } catch (ActivityNotFoundException e) {
                            Toast.makeText(mContext, R.string.toast_paly_failed, Toast.LENGTH_SHORT)
                                    .show();
                        }
                    }
                }
            });
            return convertView;
        }

        class ViewHolder {
            public TextView nameView;

            public CheckBox checkBox;
        }

        class onMyLongClick implements OnLongClickListener {
            private int position;

            private List<File> list;

            public onMyLongClick(int position, List<File> list) {
                this.position = position;
                this.list = list;
            }

            @Override
            public boolean onLongClick(View v) {
                // TODO Auto-generated method stub
                int listSize = this.list.size();
                if (isSelectionMode) {
                    isSelectionMode = false;
                    for (int i = 0; i < listSize; i++) {
                        mAdapter.isCheckBoxVisible.put(i, CheckBox.INVISIBLE);
                    }
                    mMenu.getItem(DELETE_MENU_ITEM).setVisible(false);
                    mAdapter = new RecordingListAdapter(mContext, list, position);
                    mLv.setAdapter(mAdapter);
                    String listTitle = mContext.getResources().getString(R.string.record_list);
                    toolbar.setTitle(listTitle);
                    isSelectionMode = false;
                } else {
                    isSelectionMode = true;
                    mFileDeleteList.clear();
                    mFileDeleteList.add(list.get(position));
                    mMenu.getItem(DELETE_MENU_ITEM).setVisible(true);

                    for (int i = 0; i < listSize; i++) {
                        mAdapter.isCheckBoxVisible.put(i, CheckBox.VISIBLE);
                    }

                    mAdapter = new RecordingListAdapter(mContext, list, position);
                    mLv.setAdapter(mAdapter);
                    String title = mContext.getResources().getString(R.string.num_of_selected);
                    String.format(title, mFileDeleteList.size());
                    toolbar.setTitle(String.format(title, 1));
                    isSelectionMode = true;
                }
                return true;
            }
        }
    }
}
