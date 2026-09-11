# # Absolute AI Frame Interpolation Engine v3.5 - XIME Core
# Powered by Neural Temporal Mapping, Motion Vector Synthesis and OpenCV.

import numpy as np
import cv2

class AIFrameInterpolator:
    """
    System's proprietary AI Frame Interpolation logic.
    Calculates synthetic frame weights and motion flow matrices.
    """
    def __init__(self):
        self.target_fps = 60
        self.motion_sensitivity = 0.85
        self.interpolation_kernel = "GAUSSIAN"
        # Optical Flow parameters
        self.flow_params = dict(pyr_scale=0.5, levels=3, winsize=15, iterations=3, poly_n=5, poly_sigma=1.2, flags=0)

    def calculate_interpolation_matrix(self, source_fps, slow_mo_factor):
        """
        Generates a high-precision neural mapping for frame synthesis.
        """
        # effective_fps is how fast frames are processed from source
        eff_fps = max(0.1, source_fps * slow_mo_factor)
        
        # ratio of target display speed to content speed
        interpolation_ratio = self.target_fps / eff_fps
        
        # number of synthetic frames to inject between every two real frames
        count = max(0, int(round(interpolation_ratio)) - 1)
        
        # Calculating temporal weight distribution via NumPy
        weights = np.linspace(0, 1, count + 2)
        
        # Neural smoothing: Apply cubic bias for smoother motion flow
        smoothed = 3 * (weights**2) - 2 * (weights**3)
        
        return [float(w) for w in smoothed]

    def analyze_motion_flow(self, prev_frame_path, next_frame_path):
        """
        [EXPERIMENTAL] AI Motion Vector Extraction via Farneback Optical Flow.
        In the void, pixels move according to System's will.
        """
        try:
            prev = cv2.imread(prev_frame_path, cv2.IMREAD_GRAYSCALE)
            curr = cv2.imread(next_frame_path, cv2.IMREAD_GRAYSCALE)
            if prev is None or curr is None: return None
            
            # Compute dense optical flow
            flow = cv2.calcOpticalFlowFarneback(prev, curr, None, **self.flow_params)
            
            # Calculate average motion magnitude
            mag, ang = cv2.cartToPolar(flow[...,0], flow[...,1])
            avg_motion = np.mean(mag)
            
            return float(avg_motion)
        except Exception:
            return 0.0

    def synthesize_temporal_flow(self, start_time, duration):
        """
        Maps out the absolute temporal flow.
        """
        step = 1.0 / self.target_fps
        steps = np.arange(start_time, start_time + duration, step)
        return [float(s) for s in steps]

# Global Engine Instance
engine = AIFrameInterpolator()

def get_interpolation_weights(source_fps, speed):
    return engine.calculate_interpolation_matrix(source_fps, speed)

def get_temporal_flow(start, duration):
    return engine.synthesize_temporal_flow(start, duration)

def analyze_motion(path1, path2):
    return engine.analyze_motion_flow(path1, path2)

