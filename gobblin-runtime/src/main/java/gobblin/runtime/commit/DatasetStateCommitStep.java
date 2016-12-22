/*
 * Copyright (C) 2014-2016 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.
 */

package gobblin.runtime.commit;

import gobblin.metastore.DatasetStateStore;
import gobblin.util.ClassAliasResolver;
import java.io.IOException;
import java.net.URI;

import org.apache.hadoop.fs.FileSystem;

import com.google.common.base.Preconditions;

import gobblin.annotation.Alpha;
import gobblin.commit.CommitSequence;
import gobblin.commit.CommitStep;
import gobblin.commit.CommitStepBase;
import gobblin.configuration.ConfigurationKeys;
import gobblin.runtime.FsDatasetStateStore;
import gobblin.runtime.JobState.DatasetState;
import gobblin.util.HadoopUtils;
import lombok.extern.slf4j.Slf4j;


/**
 * An implementation of {@link CommitStep} for persisting dataset states.
 *
 * @author Ziyang Liu
 */
@Alpha
@Slf4j
public class DatasetStateCommitStep extends CommitStepBase {

  private final String datasetUrn;
  private final DatasetState datasetState;
  private transient DatasetStateStore stateStore;

  private DatasetStateCommitStep(Builder<? extends Builder<?>> builder) {
    super(builder);

    this.datasetUrn = builder.datasetUrn;
    this.datasetState = builder.datasetState;
  }

  public static class Builder<T extends Builder<?>> extends CommitStepBase.Builder<T> {
    private String datasetUrn;
    private DatasetState datasetState;

    public Builder() {
      super();
    }

    public Builder(CommitSequence.Builder commitSequenceBuilder) {
      super(commitSequenceBuilder);
    }

    @SuppressWarnings("unchecked")
    public T withDatasetUrn(String datasetUrn) {
      this.datasetUrn = datasetUrn;
      return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T withDatasetState(DatasetState datasetState) {
      this.datasetState = datasetState;
      return (T) this;
    }

    @Override
    public CommitStep build() {
      Preconditions.checkNotNull(this.datasetUrn);
      Preconditions.checkNotNull(this.datasetState);

      return new DatasetStateCommitStep(this);
    }
  }

  @Override
  public boolean isCompleted() throws IOException {
    Preconditions.checkNotNull(this.datasetState);

    return this.datasetState
        .equals(getDatasetStateStore().getLatestDatasetState(this.datasetState.getJobName(), this.datasetUrn));
  }

  @Override
  public void execute() throws IOException {
    log.info("Persisting dataset state for dataset " + this.datasetUrn);
    getDatasetStateStore().persistDatasetState(this.datasetUrn, this.datasetState);
  }

  private DatasetStateStore getDatasetStateStore() throws IOException {
    if (this.stateStore == null) {
      ClassAliasResolver<DatasetStateStore.Factory> resolver =
          new ClassAliasResolver<>(DatasetStateStore.Factory.class);

      String stateStoreType = this.props.getProp(ConfigurationKeys.STATE_STORE_TYPE_KEY,
            ConfigurationKeys.DEFAULT_STATE_STORE_TYPE);

      try {
        DatasetStateStore.Factory stateStoreFactory =
            resolver.resolveClass(stateStoreType).newInstance();

        this.stateStore = stateStoreFactory.createStateStore(this.props.getProperties());
      } catch (RuntimeException e) {
        throw e;
      } catch (Exception e) {
        throw new IOException(e);
      }
    }
    return this.stateStore;
  }

}
