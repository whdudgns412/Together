package shyunku.project.together.Activities;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import shyunku.project.together.Adapters.TransactionAdapter;
import shyunku.project.together.Constants.Global;
import shyunku.project.together.Engines.FirebaseManageEngine;
import shyunku.project.together.Engines.LogEngine;
import shyunku.project.together.Objects.Chat;
import shyunku.project.together.Objects.MoneyTransaction;
import shyunku.project.together.Objects.User;
import shyunku.project.together.R;

public class MoneyTransactionActivity extends AppCompatActivity {
    private RecyclerView transactionRecyclerView;
    private RecyclerView.Adapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;
    private ArrayList<MoneyTransaction> transactions = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.money_management);

        initialSetting();
    }

    private void initialSetting(){
        transactionRecyclerView = (RecyclerView) findViewById(R.id.transaction_history_recyclerview);
        transactionRecyclerView.setHasFixedSize(true);

        mLayoutManager = new LinearLayoutManager(this);
        ((LinearLayoutManager) mLayoutManager).setReverseLayout(true);
        ((LinearLayoutManager) mLayoutManager).setStackFromEnd(true);
        transactionRecyclerView.setLayoutManager(mLayoutManager);

        mAdapter = new TransactionAdapter(transactions);
        transactionRecyclerView.setAdapter(mAdapter);
        transactionRecyclerView.setItemAnimator(new DefaultItemAnimator());

        TextView leftside = (TextView)findViewById(R.id.left_user_view);
        TextView rightside = (TextView)findViewById(R.id.right_user_view);

        Button addButton = (Button)findViewById(R.id.transaction_add_button);
        Button settleButton = (Button)findViewById(R.id.transaction_settle_button);

        final TextView totalValue = (TextView)findViewById(R.id.transaction_value_view);
        final TextView arrowDirection = (TextView) findViewById(R.id.arrow_direction);

        leftside.setText(Global.getOwner());
        rightside.setText(Global.getOpper());

        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                LayoutInflater inflater = (LayoutInflater)getSystemService(LAYOUT_INFLATER_SERVICE);
                AlertDialog.Builder builder = new AlertDialog.Builder(MoneyTransactionActivity.this);
                View viewGroup = inflater.inflate(R.layout.transaction_add_dialog, (ViewGroup)findViewById(R.id.transaction_add_layout));

                final EditText title = (EditText)viewGroup.findViewById(R.id.transaction_dialog_title);
                final EditText time = (EditText)viewGroup.findViewById(R.id.transaction_dialog_timestamp);
                final EditText value = (EditText)viewGroup.findViewById(R.id.transaction_dialog_value);
                final RadioGroup radioGroup = (RadioGroup)viewGroup.findViewById(R.id.TransactionTypeRadioGroup);

                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(System.currentTimeMillis());
                time.setText(Global.transactionDateFormat.format(cal.getTime()));

                builder.setTitle("갚을 돈/받을 돈 추가");
                builder.setView(viewGroup);

                radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(RadioGroup radioGroup, int i) {
                        int index = radioGroup.getCheckedRadioButtonId();
                        if(index == R.id.radio_payback_by_me){
                            title.setText("갚는 돈");
                            title.setEnabled(false);
                        }else{
                            title.setText("");
                            title.setEnabled(true);
                        }
                    }
                });

                builder.setNegativeButton("취소", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                });

                builder.setPositiveButton("추가", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        final int checkedButtonIndex = radioGroup.getCheckedRadioButtonId();
                        MoneyTransaction transaction = new MoneyTransaction(title.getText().toString(), checkedButtonIndex == R.id.radio_payback?Global.getOwner():Global.getOpper(), value.getText().toString() ,time.getText().toString());
                        String key = FirebaseManageEngine.getFreshLocalDBref().child(Global.rootName+"/transactions").push().getKey();

                        Map<String, Object> postVal = transaction.toMap();
                        Map<String, Object> childUpdates = new HashMap<>();
                        childUpdates.put(Global.rootName+"/transactions/"+key, postVal);

                        FirebaseManageEngine.getFreshLocalDBref().updateChildren(childUpdates);

                        //FirebaseManageEngine.sendNotificationChatMessage(chat.content);
                    }
                });

                builder.show();
            }
        });

        settleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(System.currentTimeMillis());

                if(arrowDirection.getText().equals(" = ")){
                    Global.makeToast(MoneyTransactionActivity.this, "결산할 내용이 없습니다!");
                    return;
                }

                MoneyTransaction transaction = new MoneyTransaction(Global.transactionDateFormat.format(cal.getTime()));
                String key = FirebaseManageEngine.getFreshLocalDBref().child(Global.rootName+"/transactions").push().getKey();

                Map<String, Object> postVal = transaction.toMap();
                Map<String, Object> childUpdates = new HashMap<>();
                childUpdates.put(Global.rootName+"/transactions/"+key, postVal);

                FirebaseManageEngine.getFreshLocalDBref().updateChildren(childUpdates);
            }
        });

        DatabaseReference myref = FirebaseManageEngine.getFreshLocalDB().getReference(Global.rootName+"/transactions");
        myref.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String s) {
                MoneyTransaction transaction = snapshot.getValue(MoneyTransaction.class);
                Boolean bool = (Boolean)snapshot.child("type").getValue();
                if(bool) {
                    new LogEngine().sendLog("GENERAL ADDED");
                    transactions.add(new MoneyTransaction(
                            snapshot.child("name").getValue().toString(),
                            snapshot.child("owed").getValue().toString(),
                            transaction.value,
                            transaction.timestamp
                            )
                    );
                }
                else {
                    new LogEngine().sendLog("SETTLE ADDED");
                    transactions.add(new MoneyTransaction(transaction.timestamp)
                    );
                }
                mAdapter.notifyDataSetChanged();

                int profit = 0;
                for(int i=0;i<transactions.size();i++){
                    MoneyTransaction tr = transactions.get(i);
                    if(!tr.isGeneral){
                        profit = 0;
                        continue;
                    }
                    int value = Integer.parseInt(tr.value);
                    if(tr.OwedUsername.equals(Global.getOpper())) profit += value;
                    else profit -= value;
                }

                if(profit == 0){
                    totalValue.setText("- 원");
                    arrowDirection.setText(" = ");
                    totalValue.setTextColor(ContextCompat.getColor(MoneyTransactionActivity.this, R.color.pure_white));
                }else{
                    totalValue.setText((int)Math.abs(profit)+" 원");
                    if(profit>0) {
                        arrowDirection.setText(" ← ");
                        totalValue.setTextColor(ContextCompat.getColor(MoneyTransactionActivity.this, R.color.transaction_blue));
                    }
                    else {
                        arrowDirection.setText(" → ");
                        totalValue.setTextColor(ContextCompat.getColor(MoneyTransactionActivity.this, R.color.transaction_red));
                    }
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }
}