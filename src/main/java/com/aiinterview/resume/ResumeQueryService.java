package com.aiinterview.resume;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Training modules call this under the authenticated security context and persist their own snapshots. */
@Service
@Transactional(readOnly=true)
public class ResumeQueryService {
    private final ResumeService resumes;
    public ResumeQueryService(ResumeService resumes) { this.resumes=resumes; }
    public ResumeViews.Version getConfirmedResume(String resumeId) { return resumes.confirmedResume(resumeId); }
    public ResumeViews.Project getConfirmedProject(String resumeId,String projectId) { return resumes.confirmedProject(resumeId,projectId); }
}
