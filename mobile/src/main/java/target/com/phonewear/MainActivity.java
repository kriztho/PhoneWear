package target.com.phonewear;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import android.os.StrictMode;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CheckedTextView;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.security.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;


public class MainActivity extends Activity implements GoogleApiClient.ConnectionCallbacks, DataApi.DataListener, MessageApi.MessageListener {

    static final String TAG = "MainActivity";
    private static final String START_ACTIVITY = "/start_activity";
    private static final String LIST_PATH = "/list";

    GoogleApiClient mApiClient;
    Button mSendButton;
    EditText mEditText;
    ListView mList;
    ArrayAdapter<String> mAdapter;
    ItemAdapter mItemAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Init the API Client
        mApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .build();

        //Setup the UI
        mItemAdapter = new ItemAdapter(this);
        mList = (ListView) findViewById(R.id.listView);
        mList.setAdapter(mItemAdapter);

        mEditText = (EditText) findViewById(R.id.editText);
        mSendButton = (Button) findViewById(R.id.button);
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                //Add new entry
                DataMap map = new DataMap();
                map.putString("text", mEditText.getText().toString());
                map.putBoolean("isChecked", false);
                mItemAdapter.add(map);

                int idx = mItemAdapter.getCount()-1;
                syncItem("/0", "/" + idx, map);

                //Clean box
                mEditText.setText("");
            }
        });
    }

    protected void syncList(String path, ArrayList<DataMap> list) {

    }

    protected void syncItem(String listPath, String itemPath, DataMap item) {

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

    @Override
    protected void onStart() {
        super.onStart();

        if (mApiClient != null && !(mApiClient.isConnected() || mApiClient.isConnecting()))
            mApiClient.connect();
    }

    @Override
    protected void onDestroy() {

        if (mApiClient != null) {
            mApiClient.unregisterConnectionCallbacks(this);
        }

        super.onDestroy();
    }

    @Override
    protected void onStop() {

        if (mApiClient != null) {

            Wearable.MessageApi.removeListener(mApiClient, this);
            Wearable.DataApi.removeListener(mApiClient, this);

            if (mApiClient.isConnected()) {
                mApiClient.disconnect();
            }
        }
        super.onStop();
    }


    //Google Api
    @Override
    public void onConnected(Bundle bundle) {

        Wearable.DataApi.addListener(mApiClient, this);
        Wearable.MessageApi.addListener(mApiClient, this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }


    //Message Api
    private void sendMessage(final String path, final String text) {

        new Thread(new Runnable() {
            @Override
            public void run() {
                NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(mApiClient).await();
                for (Node node : nodes.getNodes()) {
                    MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage(
                            mApiClient, node.getId(), path, text.getBytes()
                    ).await();
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mEditText.setText("");
                    }
                });
            }
        }).start();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {

    }


    //Data Api
    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {

        final List<DataEvent> events = FreezableUtils.freezeIterable(dataEvents);
        dataEvents.close();

        for (DataEvent event : events) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                Log.d(TAG, "DataItem changed: " + event.getDataItem().getUri());

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
                Log.d(TAG, "DataItem deleted: " + event.getDataItem().getUri());


            }
        }
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
                convertView = inflater.inflate(R.layout.list_checkbox_text, null);
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
                                //syncItem("/0", "/" + position, item);
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

            return convertView;
        }

        private class ViewHolder {
            TextView text;
            CheckBox isChecked;
        }
    }
}
