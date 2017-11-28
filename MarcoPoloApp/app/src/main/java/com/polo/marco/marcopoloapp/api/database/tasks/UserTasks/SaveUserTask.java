package com.polo.marco.marcopoloapp.api.database.tasks.UserTasks;

import android.os.AsyncTask;
import android.util.Log;

import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapperConfig;
import com.polo.marco.marcopoloapp.api.database.Database;
import com.polo.marco.marcopoloapp.api.database.User;

/**
 * Created by Chase on 10/30/2017.
 */

public class SaveUserTask extends AsyncTask<User, Void, Void> {
    @Override
    protected Void doInBackground(User... params) {
        Database.mapper.save(params[0], new DynamoDBMapperConfig(DynamoDBMapperConfig.SaveBehavior.CLOBBER));
        return null;
    }
}
