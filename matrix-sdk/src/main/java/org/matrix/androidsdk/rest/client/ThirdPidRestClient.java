/*
 * Copyright 2014 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
 * Copyright 2018 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.matrix.androidsdk.rest.client;

import androidx.annotation.Nullable;

import org.matrix.androidsdk.HomeServerConnectionConfig;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.core.JsonUtils;
import org.matrix.androidsdk.core.StringUtilsKt;
import org.matrix.androidsdk.core.callback.ApiCallback;
import org.matrix.androidsdk.core.callback.SimpleApiCallback;
import org.matrix.androidsdk.core.model.MatrixError;
import org.matrix.androidsdk.features.identityserver.IdentityServerV2ApiNotAvailable;
import org.matrix.androidsdk.rest.api.IdentityThirdPidApi;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.model.BulkLookupParams;
import org.matrix.androidsdk.rest.model.BulkLookupResponse;
import org.matrix.androidsdk.rest.model.IdentityServerRequest3PIDValidationParams;
import org.matrix.androidsdk.rest.model.IdentityServerRequestTokenResponse;
import org.matrix.androidsdk.rest.model.RequestOwnershipParams;
import org.matrix.androidsdk.rest.model.SuccessResult;
import org.matrix.androidsdk.rest.model.identityserver.HashDetailResponse;
import org.matrix.androidsdk.rest.model.identityserver.LookUpV2Params;
import org.matrix.androidsdk.rest.model.identityserver.LookUpV2Response;
import org.matrix.androidsdk.rest.model.pid.ThreePid;
import org.matrix.olm.OlmUtility;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ThirdPidRestClient extends RestClient<IdentityThirdPidApi> {

    /**
     * {@inheritDoc}
     */
    public ThirdPidRestClient(HomeServerConnectionConfig hsConfig) {
        super(hsConfig, IdentityThirdPidApi.class, "", JsonUtils.getGson(false), true);
    }

    /**
     * Request the ownership validation of an email address or a phone number previously set
     * by {@link ThirdPidRestClient#requestEmailValidationToken(ThreePid, String, ApiCallback)}
     *
     * @param medium       the medium of the 3pid
     * @param token        the token generated by the requestEmailValidationToken call
     * @param clientSecret the client secret which was supplied in the requestEmailValidationToken call
     * @param sid          the sid for the session
     * @param callback     asynchronous callback response
     */
    public void submitValidationToken(final String medium,
                                      final String token,
                                      final String clientSecret,
                                      final String sid,
                                      final ApiCallback<Boolean> callback) {
        RequestOwnershipParams params = RequestOwnershipParams.Companion.with(clientSecret, sid, token);
        mApi.requestOwnershipValidationV2(medium, params)
                .enqueue(new RestAdapterCallback<>("submitValidationToken",
                        null,
                        new SimpleApiCallback<SuccessResult>(callback) {
                            @Override
                            public void onSuccess(SuccessResult info) {
                                callback.onSuccess(info.success);
                            }

                            @Override
                            public void onMatrixError(MatrixError e) {
                                if (e.mStatus == HttpURLConnection.HTTP_NOT_FOUND /*404*/) {
                                    // Use legacy request
                                    submitValidationTokenLegacy(medium, token, clientSecret, sid, callback);
                                } else {
                                    super.onMatrixError(e);
                                }
                            }
                        },
                        null));
    }

    public void submitValidationTokenLegacy(final String medium,
                                            final String token,
                                            final String clientSecret,
                                            final String sid,
                                            final ApiCallback<Boolean> callback) {
        mApi.requestOwnershipValidation(medium, token, clientSecret, sid)
                .enqueue(new RestAdapterCallback<>("submitValidationTokenLegacy",
                        null,
                        new SimpleApiCallback<SuccessResult>(callback) {
                            @Override
                            public void onSuccess(SuccessResult info) {
                                callback.onSuccess(info.success);
                            }
                        },
                        null));
    }

    /**
     * Get the look up params
     */
    public void getLookupParam(final ApiCallback<HashDetailResponse> callback) {
        mApi.hashDetails().enqueue(new RestAdapterCallback<>("getLookupParam",
                null,
                new SimpleApiCallback<HashDetailResponse>(callback) {
                    @Override
                    public void onSuccess(HashDetailResponse info) {
                        callback.onSuccess(info);
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        if (e.mStatus == HttpURLConnection.HTTP_NOT_FOUND /*404*/) {
                            callback.onUnexpectedError(new IdentityServerV2ApiNotAvailable());
                        } else {
                            super.onMatrixError(e);
                        }
                    }
                },
                null));
    }

    /**
     * Retrieve user matrix id from a 3rd party id.
     *
     * @param addresses 3rd party ids
     * @param mediums   the media.
     * @param callback  the 3rd parties callback
     */
    public void lookup3PidsV2(final HashDetailResponse hashDetailResponse,
                              final List<String> addresses,
                              final List<String> mediums,
                              final ApiCallback<List<String>> callback) {
        // sanity checks
        if ((null == addresses) || (null == mediums) || (addresses.size() != mediums.size())) {
            callback.onUnexpectedError(new Exception("invalid params"));
            return;
        }

        // nothing to check
        if (0 == mediums.size()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }

        // Check that hashDetailResponse support sha256
        if (!hashDetailResponse.algorithms.contains("sha256")) {
            // We wont do better on the legacy SDK, in particular, we do not support "none"
            callback.onUnexpectedError(new Exception("sha256 is not supported"));
            return;
        }

        OlmUtility olmUtility;

        try {
            olmUtility = new OlmUtility();
        } catch (Exception e) {
            callback.onUnexpectedError(e);
            return;
        }

        final List<String> hashedPids = new ArrayList<>();

        for (int i = 0; i < addresses.size(); i++) {
            hashedPids.add(
                    StringUtilsKt.base64ToBase64Url(
                            olmUtility.sha256(addresses.get(i).toLowerCase(Locale.ROOT)
                                    + " " + mediums.get(i) + " " + hashDetailResponse.pepper)
                    )
            );
        }

        olmUtility.releaseUtility();

        LookUpV2Params lookUpV2Params = new LookUpV2Params(hashedPids, "sha256", hashDetailResponse.pepper);

        mApi.bulkLookupV2(lookUpV2Params).enqueue(new RestAdapterCallback<>("bulkLookupV2",
                null,
                new SimpleApiCallback<LookUpV2Response>(callback) {
                    @Override
                    public void onSuccess(LookUpV2Response info) {
                        handleLookupV2Success(info, hashedPids, callback);
                    }

                    // Note that we request the pepper before each request, so for now we consider the pepper cannot be wrong.
                },
                null));
    }

    /**
     * Retrieve user matrix id from a 3rd party id.
     *
     * @param addresses 3rd party ids
     * @param mediums   the media.
     * @param callback  the 3rd parties callback
     * @Deprecated Try to use first v2 API
     */
    @Deprecated
    public void lookup3Pids(final List<String> addresses, final List<String> mediums, final ApiCallback<List<String>> callback) {
        // sanity checks
        if ((null == addresses) || (null == mediums) || (addresses.size() != mediums.size())) {
            callback.onUnexpectedError(new Exception("invalid params"));
            return;
        }

        // nothing to check
        if (0 == mediums.size()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }

        BulkLookupParams threePidsParams = new BulkLookupParams();

        List<List<String>> list = new ArrayList<>();

        for (int i = 0; i < addresses.size(); i++) {
            list.add(Arrays.asList(mediums.get(i), addresses.get(i)));
        }

        threePidsParams.threepids = list;

        mApi.bulkLookup(threePidsParams).enqueue(new RestAdapterCallback<>("lookup3PidsLegacy",
                null,
                new SimpleApiCallback<BulkLookupResponse>(callback) {
                    @Override
                    public void onSuccess(BulkLookupResponse info) {
                        handleBulkLookupSuccess(info, addresses, callback);
                    }
                },
                null));
    }

    private void handleBulkLookupSuccess(
            BulkLookupResponse bulkLookupResponse,
            List<String> addresses,
            ApiCallback<List<String>> callback) {
        Map<String, String> mxidByAddress = new HashMap<>();

        if (null != bulkLookupResponse.threepids) {
            for (int i = 0; i < bulkLookupResponse.threepids.size(); i++) {
                List<String> items = bulkLookupResponse.threepids.get(i);
                // [0] : medium
                // [1] : address
                // [2] : matrix id
                mxidByAddress.put(items.get(1), items.get(2));
            }
        }

        List<String> matrixIds = new ArrayList<>();

        for (String address : addresses) {
            if (mxidByAddress.containsKey(address)) {
                matrixIds.add(mxidByAddress.get(address));
            } else {
                matrixIds.add("");
            }
        }

        callback.onSuccess(matrixIds);
    }

    private void handleLookupV2Success(
            LookUpV2Response bulkLookupResponse,
            List<String> hashedPids,
            ApiCallback<List<String>> callback) {
        List<String> matrixIds = new ArrayList<>();

        for (int i = 0; i < hashedPids.size(); i++) {
            if (bulkLookupResponse.mappings.get(hashedPids.get(i)) != null) {
                matrixIds.add(bulkLookupResponse.mappings.get(hashedPids.get(i)));
            } else {
                matrixIds.add("");
            }
        }

        callback.onSuccess(matrixIds);
    }

    public void requestEmailValidationToken(ThreePid pid, @Nullable String nextLink, ApiCallback<Void> callback) {
        IdentityServerRequest3PIDValidationParams params =
                IdentityServerRequest3PIDValidationParams.forEmail(pid.getEmailAddress(), pid.getClientSecret(), pid.getSendAttempt());

        pid.setState(ThreePid.State.TOKEN_REQUESTED);
        mApi.requestMailValidationToken(params).enqueue(new RestAdapterCallback<>("chandeBindStatus",
                null,
                new SimpleApiCallback<IdentityServerRequestTokenResponse>(callback) {
                    @Override
                    public void onSuccess(IdentityServerRequestTokenResponse response) {
                        pid.setSid(response.sid);
                        callback.onSuccess(null);
                    }
                },
                null));
    }

    public void requestPhoneNumberValidationToken(ThreePid pid, @Nullable String nextLink, ApiCallback<Void> callback) {
        IdentityServerRequest3PIDValidationParams params =
                IdentityServerRequest3PIDValidationParams.forPhoneNumber(pid.getPhoneNumber(),pid.getCountry(),
                        pid.getClientSecret(), pid.getSendAttempt());

        pid.setState(ThreePid.State.TOKEN_REQUESTED);
        mApi.requestPhoneNumberValidationToken(params).enqueue(new RestAdapterCallback<>("chandeBindStatus",
                null,
                new SimpleApiCallback<IdentityServerRequestTokenResponse>(callback) {
                    @Override
                    public void onSuccess(IdentityServerRequestTokenResponse response) {
                        pid.setSid(response.sid);
                        callback.onSuccess(null);
                    }
                },
                null));
    }
}
