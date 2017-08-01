/*
 * Copyright (C) 2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.loader.executor.api;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.ContinuousPagingSession;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Statement;
import com.datastax.driver.dse.DseCluster;
import com.datastax.loader.executor.api.statement.TableScanner;
import com.datastax.loader.executor.api.batch.RxJavaUnsortedStatementBatcher;
import com.datastax.loader.tests.utils.ZipUtils;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.reactivex.Flowable;
import io.reactivex.plugins.RxJavaPlugins;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import static com.datastax.loader.tests.utils.CsvUtils.createIpByCountryTable;
import static com.datastax.loader.tests.utils.CsvUtils.csvRecords;
import static com.datastax.loader.tests.utils.CsvUtils.prepareInsertStatement;
import static com.datastax.loader.tests.utils.CsvUtils.toBoundStatement;
import static java.util.concurrent.TimeUnit.MINUTES;

public class RxJavaBulkExecutorBenchmark {

  private static final int TOTAL_RECORDS = 70865;
  private static final int WARMUP_ITERATIONS = 10;
  private static final int MEASUREMENT_ITERATIONS = 1;
  private static final int MEASUREMENT_TIME_IN_MINUTES = 5;

  @Benchmark
  @OperationsPerInvocation(TOTAL_RECORDS)
  @BenchmarkMode(Mode.Throughput)
  @OutputTimeUnit(TimeUnit.SECONDS)
  @Warmup(iterations = WARMUP_ITERATIONS)
  @Measurement(time = MEASUREMENT_TIME_IN_MINUTES, timeUnit = MINUTES, iterations = MEASUREMENT_ITERATIONS)
  @Fork(1)
  public void benchmarkReadAsync(RxJavaBulkExecutionState state, Blackhole bh) throws Exception {
    state.executor.readAsync("SELECT * FROM read_benchmark.ip_by_country", bh::consume).get();
  }

  @Benchmark
  @OperationsPerInvocation(TOTAL_RECORDS)
  @BenchmarkMode(Mode.Throughput)
  @OutputTimeUnit(TimeUnit.SECONDS)
  @Warmup(iterations = WARMUP_ITERATIONS)
  @Measurement(time = MEASUREMENT_TIME_IN_MINUTES, timeUnit = MINUTES, iterations = MEASUREMENT_ITERATIONS)
  @Fork(1)
  public void benchmarkReadReactive(RxJavaBulkExecutionState state, Blackhole bh) throws Exception {
    state.executor.readReactive("SELECT * FROM read_benchmark.ip_by_country")
        .doOnNext(bh::consume)
        .blockingSubscribe();
  }

  @Benchmark
  @OperationsPerInvocation(TOTAL_RECORDS)
  @BenchmarkMode(Mode.Throughput)
  @OutputTimeUnit(TimeUnit.SECONDS)
  @Warmup(iterations = WARMUP_ITERATIONS)
  @Measurement(time = MEASUREMENT_TIME_IN_MINUTES, timeUnit = MINUTES, iterations = MEASUREMENT_ITERATIONS)
  @Fork(1)
  public void benchmarkReadRanges(RxJavaBulkExecutionState state, Blackhole bh) throws Exception {
    state.executor.readReactive(state.selects)
        .doOnNext(bh::consume)
        .blockingSubscribe();
  }

  @Benchmark
  @OperationsPerInvocation(TOTAL_RECORDS)
  @BenchmarkMode(Mode.Throughput)
  @OutputTimeUnit(TimeUnit.SECONDS)
  @Warmup(iterations = WARMUP_ITERATIONS)
  @Measurement(time = MEASUREMENT_TIME_IN_MINUTES, timeUnit = MINUTES, iterations = MEASUREMENT_ITERATIONS)
  @Fork(1)
  public void benchmarkReadContinuously(RxJavaBulkExecutionState state, Blackhole bh) throws Exception {
    state.continuousExecutor.readReactive(state.selects)
        .doOnNext(bh::consume)
        .blockingSubscribe();
  }

  @Benchmark
  @OperationsPerInvocation(TOTAL_RECORDS)
  @BenchmarkMode(Mode.Throughput)
  @OutputTimeUnit(TimeUnit.SECONDS)
  @Warmup(iterations = WARMUP_ITERATIONS)
  @Measurement(time = MEASUREMENT_TIME_IN_MINUTES, timeUnit = MINUTES, iterations = MEASUREMENT_ITERATIONS)
  @Fork(1)
  public void benchmarkWriteAsync(RxJavaBulkExecutionState state) throws Exception {
    state.executor.writeAsync(state.boundStatements().compose(state.batcher)).get();
  }

  @Benchmark
  @OperationsPerInvocation(TOTAL_RECORDS)
  @BenchmarkMode(Mode.Throughput)
  @OutputTimeUnit(TimeUnit.SECONDS)
  @Warmup(iterations = WARMUP_ITERATIONS)
  @Measurement(time = MEASUREMENT_TIME_IN_MINUTES, timeUnit = MINUTES, iterations = MEASUREMENT_ITERATIONS)
  @Fork(1)
  public void benchmarkWriteReactive(RxJavaBulkExecutionState state) throws Exception {
    state.boundStatements()
        .compose(state.batcher)
        .flatMap(state.executor::writeReactive)
        .blockingSubscribe();
  }


  @State(Scope.Benchmark)
  public static class RxJavaBulkExecutionState {

    private DseCluster cluster;
    private ContinuousPagingSession session;
    private ExecutorService pool;
    private DefaultRxJavaBulkExecutor executor;
    private ContinuousRxJavaBulkExecutor continuousExecutor;
    private RxJavaUnsortedStatementBatcher batcher;
    private URL csvFile;
    private PreparedStatement insert;
    private List<Statement> selects;

    @Setup(Level.Trial)
    public void init() throws IOException {

      RxJavaPlugins.setErrorHandler((t) -> {
      });

      cluster = DseCluster.builder().addContactPoint("127.0.0.1").build();
      session = (ContinuousPagingSession) cluster.connect();
      pool = Executors.newFixedThreadPool(
          Runtime.getRuntime().availableProcessors() * 2,
          new ThreadFactoryBuilder()
              .setDaemon(true)
              .setNameFormat("bulk-executor-%d")
              .build());
      executor = DefaultRxJavaBulkExecutor.builder(session).withExecutor(pool).build();
      continuousExecutor = ContinuousRxJavaBulkExecutor.builder(session).withExecutor(pool).build();
      batcher = new RxJavaUnsortedStatementBatcher(session.getCluster());

      // fixtures for write benchmarks
      session.execute("DROP KEYSPACE IF EXISTS write_benchmark");
      session.execute("CREATE KEYSPACE write_benchmark WITH replication = { \'class\' : \'SimpleStrategy\', \'replication_factor\' : 3 }");
      session.execute("USE write_benchmark");
      Path dest = Files.createTempDirectory("benchmark");
      ZipUtils.unzip("ip-by-country-all.csv.zip", dest);
      csvFile = dest.resolve("ip-by-country.csv").toUri().toURL();
      createIpByCountryTable(session);
      insert = prepareInsertStatement(session);

      // fixtures for read benchmarks
      session.execute("DROP KEYSPACE IF EXISTS read_benchmark");
      session.execute("CREATE KEYSPACE read_benchmark WITH replication = { \'class\' : \'SimpleStrategy\', \'replication_factor\' : 3 }");
      session.execute("USE read_benchmark");
      createIpByCountryTable(session);
      PreparedStatement insert = prepareInsertStatement(session);
      csvRecords(csvFile)
          .map(record -> toBoundStatement(insert, record))
          .flatMap(executor::writeReactive)
          .blockingSubscribe();
      selects = TableScanner.scan(cluster, "read_benchmark", "ip_by_country");
    }

    @TearDown(Level.Trial)
    public void shutdown() {
      executor.close();
      continuousExecutor.close();
      session.close();
      cluster.close();
    }

    private Flowable<BoundStatement> boundStatements() {
      return csvRecords(csvFile)
          .map(record -> toBoundStatement(insert, record));
    }

  }

}
