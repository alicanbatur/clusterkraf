/**
 * @author Carlton Whitehead
 */
package com.twotoasters.clusterkraf;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;

import android.os.Handler;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

public class Clusterkraf {

	private final WeakReference<GoogleMap> mapRef;
	private final Options options;
	private final ClusterTransitionsAnimation transitionsAnimation;

	private final InnerCallbackListener innerCallbackListener = new InnerCallbackListener(this);

	private final ArrayList<InputPoint> points = new ArrayList<InputPoint>();
	private ArrayList<ClusterPoint> currentClusters;
	private ArrayList<Marker> currentMarkers;
	private HashMap<Marker, ClusterPoint> currentClusterPointsByMarker = new HashMap<Marker, ClusterPoint>();
	private ArrayList<ClusterPoint> previousClusters;
	private ArrayList<Marker> previousMarkers;

	/**
	 * construct a Clusterkraf instance to manage your map with customized
	 * options.
	 * 
	 * @param map
	 *            the GoogleMap to be managed by Clusterkraf
	 * @param options
	 *            customized options
	 */
	public Clusterkraf(GoogleMap map, Options options, ArrayList<InputPoint> points) {
		this.mapRef = new WeakReference<GoogleMap>(map);
		this.options = options;
		this.transitionsAnimation = new ClusterTransitionsAnimation(map, options, innerCallbackListener);

		if (points != null) {
			this.points.addAll(points);
		}

		showAllClusters();
	}

	public void add(InputPoint inputPoint) {
		if (inputPoint != null) {
			points.add(inputPoint);
			updateClustersAndTransition();
		}
	}

	public void addAll(ArrayList<InputPoint> inputPoints) {
		if (inputPoints != null) {
			points.addAll(inputPoints);
			updateClustersAndTransition();
		}
	}

	public void replace(ArrayList<InputPoint> inputPoints) {
		clear();
		addAll(inputPoints);
	}

	/**
	 * remove markers from the map
	 */
	public void clear() {
		// we avoid GoogleMap.clear() because the current SDK leaks custom
		// bitmaps, see:
		// http://code.google.com/p/gmaps-api-issues/issues/detail?id=4703
		for (Marker marker : currentMarkers) {
			marker.remove();
		}
		currentMarkers = null;
		currentClusterPointsByMarker = null;
		points.clear();
	}

	// TODO: support removing individual markers by InputPoint

	private void buildClusters() {
		GoogleMap map = mapRef.get();
		if (map != null) {
			ClustersBuilder builder = new ClustersBuilder(map, options, previousClusters);
			builder.addAll(points);
			currentClusters = builder.build();
		}
	}

	private void drawMarkers() {
		GoogleMap map = mapRef.get();
		if (map != null && currentClusters != null) {
			currentMarkers = new ArrayList<Marker>(currentClusters.size());
			currentClusterPointsByMarker = new HashMap<Marker, ClusterPoint>(currentClusters.size());
			MarkerOptionsChooser mic = options.getMarkerOptionsChooser();
			for (ClusterPoint clusterPoint : currentClusters) {
				MarkerOptions markerOptions = new MarkerOptions();
				markerOptions.position(clusterPoint.getMapPosition());
				if (mic != null) {
					mic.choose(markerOptions, clusterPoint);
				}
				Marker marker = map.addMarker(markerOptions);
				currentMarkers.add(marker);
				currentClusterPointsByMarker.put(marker, clusterPoint);
			}
		}
	}

	private void removePreviousMarkers() {
		GoogleMap map = mapRef.get();
		if (map != null && previousClusters != null && previousMarkers != null) {
			for (Marker marker : previousMarkers) {
				marker.remove();
			}
			previousMarkers = null;
			previousClusters = null;
		}
	}

	private void updateClustersAndTransition() {
		previousClusters = currentClusters;
		previousMarkers = currentMarkers;

		buildClusters();

		GoogleMap map = mapRef.get();
		if (map != null && currentClusters != null && previousClusters != null) {
			ClusterTransitions.Builder ctb = new ClusterTransitions.Builder(map, previousClusters);
			for (ClusterPoint currentClusterPoint : currentClusters) {
				ctb.add(currentClusterPoint);
			}
			transitionsAnimation.animate(ctb.build());
		}
	}

	private void showAllClusters() {
		buildClusters();
		drawMarkers();

		GoogleMap map = mapRef.get();
		if (map != null) {
			map.setOnCameraChangeListener(innerCallbackListener.clusteringOnCameraChangeListener);
		}
	}

	private static class InnerCallbackListener implements ClusteringOnCameraChangeListener.Host, ClusterTransitionsAnimation.Host {

		private final WeakReference<Clusterkraf> clusterkrafRef;

		private final Handler handler = new Handler();

		private InnerCallbackListener(Clusterkraf clusterkraf) {
			clusterkrafRef = new WeakReference<Clusterkraf>(clusterkraf);
		}

		private final ClusteringOnCameraChangeListener clusteringOnCameraChangeListener = new ClusteringOnCameraChangeListener(this);

		@Override
		public void onClusteringCameraChange() {
			Clusterkraf clusterkraf = clusterkrafRef.get();
			if (clusterkraf != null) {
				clusterkraf.updateClustersAndTransition();
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see com.twotoasters.clusterkraf.ClusterTransitionsAnimation.Host#
		 * onClusterTransitionStarting()
		 */
		@Override
		public void onClusterTransitionStarting() {
			clusteringOnCameraChangeListener.setDirty(true);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see com.twotoasters.clusterkraf.ClusterTransitionsAnimation.Host#
		 * onClusterTransitionStarted()
		 */
		@Override
		public void onClusterTransitionStarted() {
			Clusterkraf clusterkraf = clusterkrafRef.get();
			if (clusterkraf != null) {
				clusterkraf.removePreviousMarkers();
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see com.twotoasters.clusterkraf.ClusterTransitionsAnimation.Host#
		 * onClusterTransitionFinished()
		 */
		@Override
		public void onClusterTransitionFinished() {
			Clusterkraf clusterkraf = clusterkrafRef.get();
			if (clusterkraf != null) {
				clusterkraf.drawMarkers();
				clusterkraf.transitionsAnimation.onHostPlottedDestinationClusterPoints();
			}
			clusteringOnCameraChangeListener.setDirty(false);
		}
	}

}
