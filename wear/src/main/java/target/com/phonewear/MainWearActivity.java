package target.com.phonewear;

import android.app.Activity;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Looper;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.security.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainWearActivity extends Activity implements MessageApi.MessageListener, GoogleApiClient.ConnectionCallbacks, DataApi.DataListener, GooglePlayServicesClient.OnConnectionFailedListener{

    static final String TAG = "MainWearActivity";
    private static final String LIST_PATH = "/list";

    GoogleApiClient mApiClient;
    ListView mListView;
    ItemAdapter mItemAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_wear);

        mListView = (ListView) findViewById(R.id.list);
        mItemAdapter = new ItemAdapter(this);
        mListView.setAdapter(mItemAdapter);

        Button add = (Button)findViewById(R.id.button_add);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                //Add new entry
                DataMap map = new DataMap();
                map.putString("text", getPackageName());
                map.putBoolean("isChecked", false);
                mItemAdapter.add(map);

                int idx = mItemAdapter.getCount()-1;
                syncItem("/0", "/"+idx, map);
            }
        });

        mApiClient = new GoogleApiClient.Builder( this )
                .addApi( Wearable.API )
                .addConnectionCallbacks( this )
                .build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mApiClient.connect();
    }

    @Override
    protected void onPause() {
        super.onPause();

        Wearable.DataApi.removeListener(mApiClient, this);
        Wearable.MessageApi.removeListener(mApiClient, this);
        mApiClient.disconnect();
    }





    // Google Api
    @Override
    public void onConnected(Bundle bundle) {

        Log.d(TAG, "Connected to Google Api Service");
        Wearable.MessageApi.addListener(mApiClient, this);
        Wearable.DataApi.addListener(mApiClient, this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspended(): Connection to Google API client was suspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.e(TAG, "onConnectionFailed(): Failed to connect, with result: " + result);
    }




    //Message Api
    @Override
    public void onMessageReceived(final MessageEvent messageEvent) {
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                if (messageEvent.getPath().equalsIgnoreCase(WEAR_MESSAGE_PATH)) {
//                    mAdapter.add(new String(messageEvent.getData()));
//                    mAdapter.notifyDataSetChanged();
//                }
//            }
//        });
    }





    //Data Api
    @Override
    public void onDataChanged(final DataEventBuffer dataEvents) {
        final List<DataEvent> events = FreezableUtils.freezeIterable(dataEvents);
        dataEvents.close();
        for (DataEvent event : events) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                Log.d(TAG, "Data Changed for " + event.getDataItem().getUri().getPath());

                String path = event.getDataItem().getUri().getPath();
                DataMapItem item = DataMapItem.fromDataItem(event.getDataItem());
                DataMap map = item.getDataMap();
                final DataMap m = map.get("item");

                String[] tokens = path.split("/");
                if (tokens.length > 2) {
                    final int index = Integer.parseInt(tokens[2]);
                    if (index > mItemAdapter.getCount()-1) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mItemAdapter.add(m);
                            }
                        });
                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mItemAdapter.set(index, m);
                            }
                        });
                    }
                }
            } else if (event.getType() == DataEvent.TYPE_DELETED) {
                Log.d(TAG, "Data Deleted for " + event.getDataItem().getUri().getPath());
            }
        }
    }

    public void syncItem(String listPath, String itemPath, DataMap item) {

        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(listPath + itemPath);
        putDataMapRequest.getDataMap().putDataMap("item", item);
        putDataMapRequest.getDataMap().putLong("timestamp", new Date().getTime());
        PutDataRequest request = putDataMapRequest.asPutDataRequest();

        if (!mApiClient.isConnected()) {
            return;
        }
        Wearable.DataApi.putDataItem(mApiClient, request)
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(DataApi.DataItemResult dataItemResult) {
                        if (!dataItemResult.getStatus().isSuccess()) {
                            Log.e(TAG, "ERROR: failed to putDataItem, status code: "
                                    + dataItemResult.getStatus().getStatusCode());
                        }
                    }
                });
    }

    private class ItemAdapter extends BaseAdapter {

        private ArrayList<DataMap> mItems = new ArrayList<DataMap>();
        private final Context mContext;

        private boolean sync = true;

        public ItemAdapter(Context context) {
            mContext = context;
        }

        public void add(DataMap map) {
            mItems.add(map);
            notifyDataSetChanged();
        }

        public void set(int index, DataMap map) {
            mItems.set(index, map);
            sync = false;
            notifyDataSetChanged();
            Log.d(TAG, "***** SET VALUE");
        }

        @Override
        public DataMap getItem(int position) {
            return mItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getCount() {
            return mItems.size();
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {

            final DataMap item = getItem(position);
            final ViewHolder holder;
            if(convertView == null) {
                holder = new ViewHolder();
                LayoutInflater inflater = (LayoutInflater)mContext.getSystemService( Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.list_item, null);
                convertView.setTag(holder);
                holder.text = (TextView) convertView.findViewById(R.id.text);
                holder.isChecked = (CheckBox) convertView.findViewById(R.id.checkbox);
                holder.isChecked.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                        if (item.getBoolean("isChecked") != isChecked) {

                            //Update the model
                            item.putBoolean("isChecked", isChecked);

                            //Sync only if changed by user
                            if (sync) {
                                syncItem("/0", "/" + position, item);
                                Log.d(TAG, "***** SYNC VALUE");
                            }
                            sync = true;
                        }
                    }
                });
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            if(holder.isChecked.isChecked() != item.get("isChecked"))
                holder.isChecked.setChecked(item.getBoolean("isChecked"));
            holder.text.setText(item.getString("text"));

            Log.d(TAG, "***** SET CHECKED");
            return convertView;
        }

        private class ViewHolder {
            TextView text;
            CheckBox isChecked;
        }
    }
}
