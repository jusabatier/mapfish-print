package org.mapfish.print.servlet.job.impl;

import java.util.Date;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.mapfish.print.config.access.AccessAssertion;
import org.mapfish.print.servlet.job.PrintJobEntry;
import org.mapfish.print.servlet.job.PrintJobResult;
import org.mapfish.print.servlet.job.PrintJobStatus;

/** Represent a print job that has completed. Contains the information about the print job. */
@Entity
@Table(name = "print_job_statuses")
public class PrintJobStatusImpl implements PrintJobStatus {
  @Embedded
  @OneToMany(targetEntity = PrintJobEntryImpl.class)
  private final PrintJobEntry entry;

  @Id
  @JdbcTypeCode(SqlTypes.LONGVARCHAR)
  private String referenceId;

  @Column
  @Enumerated(EnumType.STRING)
  private PrintJobStatus.Status status = PrintJobStatus.Status.WAITING;

  @Column private Long completionTime;

  @Column private long requestCount;

  @JdbcTypeCode(SqlTypes.LONGVARCHAR)
  private String error;

  @OneToOne(targetEntity = PrintJobResultImpl.class, cascade = CascadeType.ALL, mappedBy = "status")
  @JoinColumn(name = "reference_id")
  private PrintJobResult result;

  private transient long waitingTime;

  private transient Long statusTime;

  /** Constructor. */
  public PrintJobStatusImpl() {
    this.entry = null;
  }

  /**
   * Constructor.
   *
   * @param entry the PrintJobEntry.
   * @param requestCount request count
   */
  public PrintJobStatusImpl(final PrintJobEntry entry, final long requestCount) {
    this.referenceId = entry.getReferenceId();
    this.entry = entry;
    this.requestCount = requestCount;
  }

  @Override
  public PrintJobEntry getEntry() {
    return this.entry;
  }

  @Override
  public Long getCompletionTime() {
    return this.completionTime;
  }

  public void setCompletionTime(final Long completionTime) {
    this.completionTime = completionTime;
  }

  @Override
  public long getRequestCount() {
    return this.requestCount;
  }

  public void setRequestCount(final long requestCount) {
    this.requestCount = requestCount;
  }

  @Override
  public String getError() {
    return this.error;
  }

  public void setError(final String error) {
    this.error = error;
  }

  @Override
  public PrintJobStatus.Status getStatus() {
    return this.status;
  }

  public void setStatus(final PrintJobStatus.Status status) {
    this.status = status;
  }

  @Override
  public PrintJobResult getResult() {
    return this.result;
  }

  /**
   * Set the result.
   *
   * @param result The result
   */
  public void setResult(final PrintJobResult result) {
    this.result = result;
  }

  @Override
  public String getReferenceId() {
    return this.referenceId;
  }

  @Override
  public long getStartTime() {
    return getEntry().getStartTime();
  }

  @Override
  public AccessAssertion getAccess() {
    return getEntry().getAccess();
  }

  @Override
  public String getAppId() {
    return getEntry().getAppId();
  }

  @Override
  public Date getStartDate() {
    return getEntry().getStartDate();
  }

  @Override
  public Date getCompletionDate() {
    return getCompletionTime() == null ? null : new Date(getCompletionTime());
  }

  @Override
  public long getElapsedTime() {
    if (this.completionTime != null) {
      return this.completionTime - getEntry().getStartTime();
    } else if (this.statusTime != null) {
      // TODO: are we sure about that? Makes MapPrinterServletTest.doCreateAndPollAndGetReport
      // unstable
      return this.statusTime - getEntry().getStartTime();
    } else {
      return System.currentTimeMillis() - getEntry().getStartTime();
    }
  }

  @Override
  public boolean isDone() {
    return getStatus() != PrintJobStatus.Status.RUNNING
        && getStatus() != PrintJobStatus.Status.WAITING;
  }

  @Override
  public long getWaitingTime() {
    return this.waitingTime;
  }

  @Override
  public void setWaitingTime(final long waitingTime) {
    this.waitingTime = waitingTime;
  }

  public Long getStatusTime() {
    return this.statusTime;
  }

  public void setStatusTime(final Long statusTime) {
    this.statusTime = statusTime;
  }
}
